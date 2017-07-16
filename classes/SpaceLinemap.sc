/*
    SpaceTracker for SuperCollider 
    Copyright (c) 2013 - 2017 Carlo Capocasa. All rights reserved.
    https://capocasa.net

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
*/


// A class for converting single "lines" of data
// from their symbolic representation ("1 4 c2")
// into numeric representation that can be saved
// in a standard sound file or pumped down a Bus

SpaceLinemap {

  classvar
    <namings,
    <namingClasses,
    <namingObjects
  ;

  var
    <>naming,
    <>namingMapper,
    <>namingClass,
    <>barLength = 4,
    <>defaultDivisor = 4,
    <>zeroNote = 0 // avoid magic number
  ;

  classvar
    namingPrefix = "SpaceNaming"
  ;

  *initClass {
    namingClasses = Class
      .allClasses
      .select({|class| class.name.asString.beginsWith(namingPrefix) })
      .reject({|class| class == SpaceNamingError })
    ;
    namings = this.namingClasses
      .collect({|class| this.namingFromClass(class) })
    ;
  }

  *new {
    arg naming;
    ^super.newCopyArgs(naming).init;
  }

  *basicNew {
    ^super.new;
  }

  init {
    naming = naming.asSymbol;
    namingClass = this.class.namingClass(naming);
    if (namingClass.isNil) {
      SpaceLinemapError(
        "Could not find naming class"
        + namingClass.asCompileString
        + "for naming"
        + naming.asCompileString
        ++ ".\nPlease make it available, or use one of:"
        + this.class.namings.collect({|n|n.asCompileString}).join(",")
      ).throw;
    };
    namingMapper = namingClass.new;
  }

  *namingClass {
    arg naming;
    var str = naming.asString;
    ^(namingPrefix ++ str.removeAt(0).toUpper ++ str.toLower).asSymbol.asClass;
  }

  *namingFromClass{
    arg class;
    ^class.name.asString.copyRange(namingPrefix.size,class.name.asString.size).toLower.asSymbol;
  }

  convertToSymbolic {
    arg line;
    var time, divisor;
  
    line = Array.newFrom(line);
   
    time = line.removeAt(0);

    if (line.size == 0) {
      SpaceLinemapError("Time but no value found for line %".format([time]++line)).throw;
    };

    line=this.mapSymbolic(line);
    
    // For note length, just make everything specified
    // in quarter notes. This could be made more powerful later.
    // It is assumed that a quarter note corresponds to one second by default
    // TODO: allow other default quarter notes as a first step towards
    // making recorded SpaceTracker more readable
    divisor = if(time == 0, 0, defaultDivisor);
    
    line=line.addFirst(divisor);
    line=line.addFirst(time);

    if (line.occurrencesOf(0) == line.size) {
      line = [0]; // Syntactic sugar: null line is a single zero
    };

    ^line;
  }
  
  convertToNumeric {
    arg line;
    var
      time,
      divisor
    ;
    
    if (false == line.isArray) {
      line = [line];
    };

    if (line.size < 2) {
      line=line.add(defaultDivisor);
    };
    
    if (line.size < 3) {
      line=line.add(zeroNote);
    };

    // Detect note convertToSymbolic
    time = line.removeAt(0);
    divisor = line.removeAt(0);

    line = this.mapNumeric(line);

    // First two numbers are integers - assume "note" style line
    // So calculate time float from first two numbers, and shorten
    // the line
    time = this.convertToNumericTime(time, divisor);
 
    line = line.addFirst(time);

    for(0, line.size-1, {
      arg i;
      line[i] = (line[i] ? 0) .asFloat;
    });

    ^FloatArray.newFrom(line);
  }

  convertToNumericTime {
    arg time, divisor;
    if(divisor==0, { ^0 });
    ^time * barLength / divisor;
  }

  mapSymbolic {
    arg line;
    if (namingMapper.respondsTo(\strings)) {
      ^namingMapper.strings(line);
    };
    line[0] = namingMapper.string(line[0].asInteger) ?? 0;
    ^line;
  }

  mapNumeric {
    arg line;
    var note;
    if (namingMapper.respondsTo(\numbers)) {
      ^namingMapper.numbers(line);
    };
    note = namingMapper.number(line[0]);
    if (line[0].isNil) {
      SpaceNamingError("Could not find numeric value for note %".format(line[0])).throw;
    };
    line[0] = note;
    ^line;
  }

  *newFromExtension {
    arg filename;
    var naming = filename.splitext[1];
    ^if(naming.isNil){nil}{this.new(naming)};
  }

  *newFromTree {
    arg tree;
    var ns, n;
    ns = namingClasses.reject{|c|c.findMethod(\number).isNil}.collect{|c|c.new};
    tree.parse {
      arg line;
      ns.do {|n|
        if (line.isArray) {
          if (line[2] != 0 && line[2].notNil) {
            if (n.number(line[2]).notNil) {
              ^this.new(this.namingFromClass(n.class));
            }
          }
        };
      }
    };
    ^nil;
  }
}

SpaceLinemapError : Error {
}

SpaceNamingError : Error {
}

