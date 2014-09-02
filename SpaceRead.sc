
SpaceRead {
  var
    <>tree,
    <>linemap,
    <>sounds,
    <>lineSize,

    // algorithm state
    index,
    time,
    times,
    indentTime,
  
    // algorithm by-iteration variables
    line,
    indent,
    lastIndent,
  
    // preprocess state
    <>polyphony = 1, // gets auto-adjusted
    simulatedWrites
  ;

  *new {
    arg tree, linemap;
    ^super.newCopyArgs(tree, linemap).init;
  }

  init {
    lineSize = 0;
    this.pre;
  }
  
  pre {
    
    this.initNumeric;

    tree.parse({
      arg arg_line, arg_indent, arg_lastIndent;
      
      line = arg_line;
      indent = arg_indent;
      lastIndent = arg_lastIndent;
      
      if(this.determine) {
        this.prePauseRecord;

        this.convert;
        
        if (line.size > lineSize) {
          lineSize = line.size;
        };
        
        this.record;
      }
    });
  
    polyphony = times.select({arg time; time > 0}).size;
  }

  initNumeric {
    index = 0;
    time = 0;
    indentTime = 0;
    times = Array.fill(polyphony, 0);
  }

  isIndentOdd {
    var isOdd = indent % 2 == 1;
    ^isOdd;
  }

  hasIndentIncreased {
    var hasIncreased = indent > lastIndent ;
    ^hasIncreased;
  }

  hasIndentDecreased {
    var hasDecreased = indent < lastIndent;
    ^hasDecreased;
  }

  setIndentTime {
    indentTime = times.maxItem;
  }

  setIndex {
    //index = times.minIndex; // Spread out; original rudimentary
    //index = times.indexOf(times.select({arg time; time <= indentTime;}).maxItem); // Use shortest distance
    index = times.detectIndex({arg t; t < indentTime;}); // Use first available
    if (index.isNil) {
      index = times.size;
      times=times.add(0);
    };
    time = times[index];
  }

  isDrop {
    var isDrop = time > indentTime;
    ^isDrop;
  }

  isIndentEven {
    var isEven = indent % 2 == 0;
    ^isEven;
  }

  determine {
    //[indent, lastIndent,((lastIndent - indent).abs * 0.5).round,indentTimes].postln;
    if (line.isNil) {
      ^false;
    };
    if (line == 0) {
      ^false;
    };
    if (line[0] == 0) {
      ^false;
    };
    if (line[1] == 0) {
      ^false;
    };
    if (this.isIndentOdd, {
      
      // Odd indent does parallelization, so we figure out
      // which channel to use
      
      // Keep track of indentTime by indent level
      // No note of a higher indent can come sooner than this
      if (this.hasIndentIncreased) {
        this.setIndentTime;
      };
      
      this.setIndex;

      if (this.isDrop, {
        (this.class.name + "dropped note" + line).postln;
        ^false;
      });
    });

    if (this.isIndentEven, {
      if (this.hasIndentDecreased) {
        this.setIndentTime;
      };
    });
    
    ^true; 
  }

  toNumeric {

    this.initNumeric;

    tree.parse({
      arg arg_line, arg_indent, arg_lastIndent;
      line = arg_line;
      indent = arg_indent;
      lastIndent = arg_lastIndent;
      
      if (this.determine) {
        //// Good, we figured out which channel we can use from
        //// indentation. Now insert the note.
 
        this.prePause;
        this.prePauseRecord;

        this.convert;
        this.pad;
        this.write;
        this.record;

        // Must keep this debug line!
        
        //[index,linemap.convertToSymbolic(line),times].postln;
      };
    });

    this.close;
  
    ^sounds;
  }

  pad {
    if (line.size < sounds[index].numChannels) {
      line = line.addAll(Array.fill(sounds[index].numChannels-line.size, 0));
    }
  }

  prePauseRecord {
    if (times[index] < indentTime) {
      times[index] = indentTime;
    }
  }

  prePause {
    // Insert pre-pause if necessary
    // Parallel, so relative to indentTime when parallel started
    // Fill up with pause
    if (times[index] < indentTime) {
      // [\prepause, times[index], indentTime].postln;
      sounds[index].writeData(FloatArray.fill(sounds[index].numChannels, 0).put(0, indentTime-times[index]));
    };
  }

  write {
    // Insert main line
    sounds[index].writeData(line);
  }

  convert {
    line = linemap.convertToNumeric(line);
  }

  record {
    times.atInc(index, line[0]);
  }

  close {
    sounds.do({
      arg sound;
      sound.close;
    });
  }
}

