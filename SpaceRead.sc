
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
    indentTimes,
    lastIndentTime,
    maxIndentTime,
  
    // algorithm by-iteration variables
    line,
    indent,
    lastIndent
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
    maxIndentTime = 0;
    times = Array.fill(sounds.size, 0);
    indentTimes = List.new.add(0);
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

  indentTimeIncrease {
    indentTime = times.maxItem;
    lastIndentTime = 0;
    ((indent - lastIndent) * 0.5).round.asInteger.do({
      indentTimes.add(indentTime);
    });
  }

  indentTimeDecrease {
    ((lastIndent - indent) * 0.5).round.asInteger.do({
      indentTimes.pop;
    });
    indentTime = lastIndentTime;
  }

  determineNextIndex {
    index = times.minIndex;
    time = times[index];
    if (time > maxIndentTime) {
      maxIndentTime = time;
    };
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
        this.indentTimeIncrease;
      };
      
      this.determineNextIndex;

      if (this.isDrop, {
        (this.class.name + "dropped note" + line).postln;
        ^nil;
      });
    });

    if (this.isIndentEven, {
      if (this.hasIndentDecreased) {
        this.indentTimeDecrease;
      };
    });
    
    //// Good, we figured out which channel we can use from
    //// indentation. Now insert the note.
    
    this.prePause;

    this.write;

    // Must keep this debug line!
    
    [index,linemap.convertToSymbolic(line),times].postln;
  
  }

  toNumeric {
    
    this.initNumeric;
    
    tree.parse({
      arg arg_line, arg_indent, arg_lastIndent;
      line = arg_line;
      indent = arg_indent;
      lastIndent = arg_lastIndent;
      this.iterate;
    });

    this.close;
  
    ^sounds;
  }

  prePause {
    // Insert pre-pause if necessary
    // Parallel, so relative to indentTime when parallel started
    // Fill up with pause
    [\prepause, times[index], indentTime].postln;
    if (times[index] < indentTime) {
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

