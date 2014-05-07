
SpaceRead {
  var
    tree,
    sounds,
    linemap,
    totaltimes,
  
    // algorithm state
    index,
    time,
    times,
    indentTime,
    indentTimes,
  
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
    times = Array.fill(sounds.size, 0);
    indentTimes = List.new.add(0);
  }

  isIndentOdd {
    ^indent % 2 == 1;
  }

  hasIndentIncreased {
    ^indent > lastIndent ;
  }

  hasIndentDecreased {
    ^ indent < lastIndent;
  }

  indentTimeIncrease {
    var num;
    indentTime = times.maxItem;
    ((indent - lastIndent) * 0.5).round.asInteger.do({
      indentTimes.add(indentTime);
    });
  }

  indentTimeDecrease {
    ((lastIndent - indent) * 0.5).round.asInteger.do({
      indentTimes.pop;
    });
    indentTime = indentTimes.last;
  }

  track {
    index = times.minIndex;
    time = times[index];
  }

  isDrop {
    ^time > indentTime;
  }

  isIndentEven {
    ^ indent % 2 == 0;
  }

  toNumeric {
    
    this.initNumeric;
    
    tree.parse({

      // [indent, lastIndent,((lastIndent - indent).abs * 0.5).round,indentTimes].postln;
      
      block {
        arg continue;
        if (line.isNil) {
          continue.();
        };
        if (this.isIndentOdd, {
          
          // Odd indent does parallelization, so we figure out
          // which channel to use
          
          // Keep track of indentTime by indent level
          // No note of a higher indent can come sooner than this
          if (this.hasIndentIncreased) {
            this.indentTimeIncrease;
          };
          
          this.track;

          if (this.isDrop, {
            (this.class.name + "dropped note" + line).postln;
            continue.();
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
        // [index,line,times].postln;
      };
    });

    this.close;
  
    ^sounds;
  }

  prePause {
    // Insert pre-pause if necessary
    // Parallel, so relative to indentTime when parallel started
    // Fill up with pause
    if (times[index] < indentTime) {
      sounds[index].writeData(FloatArray.fill(sounds[index].numChannels, 0).put(0, indentTime-times[index]));
      times[index] = indentTime;
    };
  }

  write {
    // Insert main line
    line = linemap.convertToNumeric(line);
    sounds[index].writeData(line);
    times[index] = times[index] + line[0];
  }

  close {
    sounds.do({
      arg sound;
      sound.close;
    });
  }
}

