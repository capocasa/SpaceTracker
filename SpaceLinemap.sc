
// A class for converting single "lines" of data
// from their symbolic representation ("1 4 c2")
// into numeric representation that can be saved
// in a standard sound file or pumped down a Bus

SpaceLinemap {

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

  *new {
    arg naming;
    ^super.newCopyArgs(naming).init;
  }

  init {
    naming = naming.asSymbol;
    namingClass = this.namingClassName.asClass;
    if (namingClass.isNil) {
      SpaceLinemapError(
        "Could not find naming class"
        + this.namingClassName.asCompileString
        + "for naming"
        + naming.asCompileString
        ++ ".\nPlease make it available, or use one of:"
        + this.namings.collect({|n|n.asCompileString}).join(",")
      ).throw;
    };
    namingMapper = namingClass.new;
  }

  namingClasses {
    ^Class
      .allClasses
      .select({|class| class.name.asString.beginsWith(namingPrefix) })
      .collect({|class| class.name.asString })
    ;
  }

  namings {
    ^this.namingClasses
      .collect({|name| name.copyRange(namingPrefix.size,name.size).toLower.asSymbol })
    ;
  } 

  namingClassName {
    var str = naming.asString;
    ^(namingPrefix ++ str.removeAt(0).toUpper ++ str.toLower).asSymbol;
  }

  convertToSymbolic {
    arg line;
    var time, divisor;
  
    line = Array.newFrom(line);
   
    time = line.removeAt(0);

    line=this.mapSymbolic(line);
    
    // For note length, just make everything specified
    // in quarter notes. This could be made more powerful later.
    divisor = if(time == 0, 0, defaultDivisor);

    time = time * divisor;
    
    line.addFirst(divisor);
    line.addFirst(time);

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
    line[0] = namingMapper.string(line[0]);
    ^line;
  }

  mapNumeric {
    arg line;
    if (namingMapper.respondsTo(\numbers)) {
      ^namingMapper.numbers(line);
    };
    line[0] = namingMapper.number(line[0]);
    ^line;
  }
}

SpaceLinemapError : Error {
}

SpaceNamingError : Error {
}

