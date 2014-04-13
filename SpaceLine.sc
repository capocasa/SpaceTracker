
// A class for converting single "lines" of data
// from their symbolic representation ("1 4 c2")
// into numeric representation that can be saved
// in a standard sound file or pumped down a Bus

SpaceLine {

  var
    naming,
    namingMapper,
    namingClass,
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
      (
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
    var time, divisor, note;
  
    line = Array.newFrom(line);
   
    time = line[0];
    note = line[1];
    
    // For note length, just make everything specified
    // in quarter notes. This could be made more powerful later.
    divisor = if(time == 0, 0, defaultDivisor);
    
    note = this.convertToSymbolicNote(note);
    time = time * divisor;

    line[0] = time;
    line[1] = note;
    
    line = line.insert(1, divisor);

    if (line.occurrencesOf(0) == line.size) {
      line = [0]; // Syntactic sugar: null line is a single zero
    };

    ^line;
  }
  
  convertToNumeric {
    arg line;
    var
      time,
      divisor,
      note
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
    time = line[0];
    divisor = line[1];
    
    // First two numbers are integers - assume "note" style line
    // So calculate time float from first two numbers, and shorten
    // the line
    time = this.convertToNumericTime(time, divisor);
    note = line[2];

    line.removeAt(1);
    
    note = this.convertToNumericNote(note);

    line[0] = time;
    line[1] = note;

    for(0, line.size-1, {
      arg i;
      line[i] = (line[i] ? 0) .asFloat;
    });

    ^FloatArray.newFrom(line);
  }

  convertToNumericTime {
    arg time, divisor;
    ^time / divisor;
  }

  convertToSymbolicNote {
    arg note;
    ^namingMapper.string(note);
  }

  convertToNumericNote {
    arg note;
    ^namingMapper.number(note);
  }

}

