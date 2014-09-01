
SpaceRead {
  var
    tree,
    sounds,
    linemap,
  
    // algorithm state
    index,
    time,
    times,
    indentTime,
  
    // algorithm by-iteration variables
    line,
    indent,
    lastIndent,
  
    // info gathered in first pass
    lineSize,
    polyphony
  ;

  *new {
    arg tree, sounds, linemap;
    ^super.newCopyArgs(tree, sounds, linemap).init;
  }

  init {
  }

  initNumeric {
    index = 0;
    time = 0;
    indentTime = 0;
    times = Array.fill(sounds.size, 0);
    lineSize = 0;
    polyphony = 0;
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
    index = times.minIndex;
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

  iterate {
    //[indent, lastIndent,((lastIndent - indent).abs * 0.5).round,indentTimes].postln;
    if (line.isNil) {
      ^nil;
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
        // (this.class.name + "dropped note" + line).postln;
        ^nil;
      });
    });

    if (this.isIndentEven, {
      if (this.hasIndentDecreased) {
        this.setIndentTime;
      };
    });
    
    //// Good, we figured out which channel we can use from
    //// indentation. Now insert the note.
    
    this.prePause;

    this.write;

    // Must keep this debug line!
    
    //[index,linemap.convertToSymbolic(line),times].postln;
  
  }

  toNumeric {
    
    this.initNumeric;
   
    // first pass: get polyphony and line size

    tree.parse({
      arg arg_line, arg_indent, arg_lastIndent;
      if (arg_line.size > lineSize) {
        lineSize = arg_line.size;
      };
    });

    tree.parse({
      arg arg_line, arg_indent, arg_lastIndent;
      line = arg_line;
      indent = arg_indent;
      lastIndent = arg_lastIndent;
      this.pad;
      this.iterate;
    });

    this.close;
  
    ^sounds;
  }

  pad {
    if(line.size < lineSize) {
      line = line.addAll(Array.fill(lineSize - line.size, 0));
    };
  }

  prePause {
    // Insert pre-pause if necessary
    // Parallel, so relative to indentTime when parallel started
    // Fill up with pause
    if (times[index] < indentTime) {
      // [\prepause, times[index], indentTime].postln;
      sounds[index].writeData(FloatArray.fill(sounds[index].numChannels, 0).put(0, indentTime-times[index]));
      times[index] = indentTime;
    };
  }

  write {
    // Insert main line
    line = linemap.convertToNumeric(line);
    sounds[index].writeData(line);
    times.atInc(index, line[0]);
  }

  close {
    sounds.do({
      arg sound;
      sound.close;
    });
  }
}

