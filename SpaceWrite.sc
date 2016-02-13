
// Writes SpaceTracker files from their rendered soundfile form

SpaceWrite {
  
  var
    soundsInit,
    tree,
    linemap,
    sounds,
    
    // First pass state (sections with iteration)
    previousOverlap,
    latestEnd,
    previousNoteEnd,
    previousType,
    
    // First pass reassign (gets reassigned after each iteration)
    index,
    overlapBackward,
    overlapForward,
    overlap,
    currentSectionParallel,
    type,
    
    // Second pass state (commented out if re-used from first pass)
      // index,
      // currentSectionParallel,
      // previousNoteEnd,
    currentNoteEnd,
    currentSectionBegin,
    nextSectionBegin,
    parallelGroupIndex,
    
    // Second pass reassign
    line,
    indent,

    // Transfer state (used to convey information from the first to the second pass)
    <sections,
    currentSectionIndex,
  
    // Iteration state (used by soundsDo)
    lines,
    begins,
    ends,
    times,
    pauseIndex,
    polyphony,
    numChannels,
    consumed,
    depleted
  ;

  init {
  }

  *new {
    arg sounds,tree,linemap;
    ^super.newCopyArgs(sounds,tree,linemap).init;
  }

  soundsDo {
    arg action;
    
    polyphony = sounds.size;
    numChannels = sounds[0].numChannels;
    lines = Array.newClear(polyphony);
    begins = Array.fill(polyphony, 0);
    ends = Array.fill(polyphony, 0);

    while ({true}, {
      // Fill up a buffer of one line per polyphonic channel
      // (used to locate note ends and null notes)

      // as opposed to lines.size.do or lines.reverseDo, this
      // allows removeAt with correct indices
      
      depleted = List[];
      lines.size.reverseDo({
        arg i;
        var line;
        line = lines[i];

        // Make sure this element of the lines buffer is full, not nil
        // decrease buffer size if sound has exhausted

        // Line can be nil, when:
        // - just initialized
        // - consumed and made note of pause

        // - consumed and written to tree file
        if ( line.isNil ) {
          line = FloatArray.newClear(numChannels);
          sounds[i].readData(line);
          
          if (line.size == numChannels, {
            lines.put(i, line);
            
            ends.atInc(i, line[0]);
            
          },{
            depleted.add(i);

            sounds.removeAt(i);
            lines.removeAt(i);
            begins.removeAt(i);
            ends.removeAt(i);
          });
        };
      });
    
      // Termination when all soundfiles depleted
      if (lines.size == 0) {
        ^this;
      };
      
      pauseIndex = lines.collect {|line| line[1]}.collect{|note| note != 0}.indexOf(false);

      consumed = action.value; // Return index of note to consume, or nil to not consume any note

      if (consumed.notNil) {
        // Beginning and end of consumed note are not the same.
        // End of consumed note will increase again when received new
        // line from soundfile
        begins.atInc(consumed, lines.at(consumed).at(0));

        lines.put(consumed, nil);
      };

    });
  }
  
  resetSounds {
    sounds = soundsInit.copy;
    sounds.do({
      arg sound;
      sound.seek(0);
    });
  }

  initFirstPass {
    sections = List[];
    previousOverlap=false;
    latestEnd=0;
    previousNoteEnd=0;
    previousType=nil; 
  }

  firstPass {
    
    this.soundsDo({
      // Default values
      overlap = false;
      overlapBackward = false;
      overlapForward = false;

      // Let's get started!

      // Loop until all lines from all sound files have been consumed   
      if (pauseIndex.isNil, {
        index = begins.minIndex;
      },{
        index = pauseIndex;
      });
      
      if (pauseIndex.isNil, {
        // detect overlap
        overlapBackward = previousNoteEnd > begins[index];
        
        if (begins.size > 1, {
          var index2 = begins.order[1];
          if (ends[index] > latestEnd) {
            latestEnd = ends[index];
          };
          overlapForward = latestEnd > begins[index2];
        },{
          overlapForward = false;
        });

        overlap = overlapBackward || overlapForward;
        // detect section change
        currentSectionParallel = nil;
        if (overlap && (false == previousOverlap)) {
          currentSectionParallel = true;
        };
        
        if ((false == overlap) && previousOverlap) {
          currentSectionParallel = false;
        };
 
        previousNoteEnd = ends[index];
      
        // Lookbehind
        previousOverlap = overlap;
      });
        
      //[\overlaps, linemap.convertToSymbolicNote(notes[index]), overlap, previousOverlap].postln;
      
      // Debug
      
      // Keep this debug output around, it's the bread
      // and butter of developing this algorithm more easily
      /*
      [
        switch(currentSectionParallel, false, "<", nil, " ", true, ">"),
        if(overlap, "8", "o"),
        //if(previousOverlap, \previousOverlap, \nopreviousOverlap),
        if(overlapBackward, ":", "."),
        if(overlapForward, "=", "-"),
        end: ends[index],
        note: linemap.convertToSymbolicNote(notes[index]),
        index: index
        //time: times[index]
      ].postln;
      */
      // Save guidance to inform the second pass
      //sections=sections.add(type);
      //sections=sections.add(0);
      //sections.atInc(sections.size-1);
      if (currentSectionParallel.notNil) {
        sections.add(currentSectionParallel);
        sections.add(begins[index]);
      }{
        // TODO: prettier solution for initial section when not parallel begin
        if (sections.size==0) {
          sections.add(false);
          sections.add(0);
        };
      };

      // Return value marks consumed
      index;
    });
  }

  // Second pass submethods

  determineSection {
    //([\val, begins.minItem] ++ begins ++ [nextSectionBegin]).postln;
    if (begins.minItem >= nextSectionBegin) {
      this.advanceSection;
    }
  }

  determineIndex {
    if (currentSectionParallel, {
      if (this.nextNoteIsInNextSection, {
        //("        "++\parallelSetIndex).postln;
        index = begins.minIndex;
        parallelGroupIndex = index;
      },{

        index = parallelGroupIndex;
        if (index >= begins.size) {
          // depleted before group finish
          index = begins.minIndex;
          parallelGroupIndex = index;
        };
      });
    },{
      index = begins.minIndex;
      parallelGroupIndex = index;
    });
    previousNoteEnd = currentNoteEnd;
    currentNoteEnd = ends.at(index);
  }

  determineIndent {
    if (currentSectionParallel) {
      if (begins[index] == currentSectionBegin) {
        indent = 1;
      } {
        indent = 2;
      }
    }{
      indent = 0;
    };
  }

  prepareLine {
    line = lines[index];
    //([\preConvert]++line).postln;
    //([\naming, linemap.naming]).postln;
    line = linemap.convertToSymbolic(line);
    //([\postConvert]++line).postln;
  }

  writeLine {
    tree.write(line, indent);
  }

  moreInPresentSection {
    var return;
    return = (ends.minItem < nextSectionBegin);
    ^ return;
  }

  nextNoteIsInNextSection {
    ^ (currentNoteEnd >= nextSectionBegin);
  }

  advanceSection {
    currentSectionIndex = currentSectionIndex + 2;
    currentSectionParallel = sections[currentSectionIndex];
    currentSectionBegin = sections[currentSectionIndex + 1];
    nextSectionBegin = sections[currentSectionIndex + 3] ?? 2147483647; // TODO: replace maxInt with song length
  }

  initSecondPass {
    currentSectionIndex = -2;
    this.advanceSection;
    currentNoteEnd = 0;
    previousNoteEnd = 0;
  }

  writeBreakIfRequired {
    
  }

  shortenOverlappingPauses {
    var startOver;
    if ((lines[index][1] == 0) && (currentNoteEnd > nextSectionBegin)) {
      lines[index].atDec(0, nextSectionBegin - begins[index]);
      begins.atInc(index, nextSectionBegin - begins[index]);
      startOver = true;
    }{
      startOver = false;
    }
    ^startOver;
  }

  secondPass {

    this.soundsDo({

      while {
        this.determineSection;
        this.determineIndex;
        this.shortenOverlappingPauses;
      };
      if (index.notNil) {
        this.determineIndent;
        this.writeBreakIfRequired;
        this.prepareLine;
        this.writeLine;
        this.debugSecondPass;
      };
      index;
    });
  }

  debugSecondPass {
    // Debug output, keep around
    [
      String.fill(indent, $ ),
      line[2], $ ,
      //if(index.isNil, if(this.nextNoteIsInNextSection, $o, $.), $-),
      switch(currentSectionParallel,nil,$|, true, $=, false, $-), $ ,
      \previousNoteEnd++$:++if(index.isNil, $-, previousNoteEnd), $ ,
      \currentNoteEnd++$:++if(index.isNil, $-, currentNoteEnd), $ ,
      \currentSectionBegin++$:++currentSectionBegin, $ ,
      \nextSectionBegin++$:++nextSectionBegin, $ ,
      'begins[index]'++$:++begins[index],$ ,
      \moreInPresentSection++$:++this.moreInPresentSection, $ ,
      \currentSectionParallel++$:++currentSectionParallel, $ , 
      \nextNoteIsInNextSection++$:++this.nextNoteIsInNextSection, $  ,
      \pauseIndex++$:++pauseIndex, $  
      //begins,
      //ends,
    ].join.postln;
  }

  numericTo {
    // First pass: Discover overlaps in sound files
    this.resetSounds;
    this.initFirstPass;
    this.firstPass;

//    block {
//      var debug = [\sections];
//      sections.pairsDo({
//        arg parallel, time;
//        debug=debug
//          .add(if(parallel, \parallel, \sequential))
//          .add(time)
//        ;
//      });
//      debug.postln;
//    };

    // Second pass: Write to tree using information collected in first pass
    this.resetSounds;
    this.initSecondPass;
    this.secondPass;
  }
}

SpaceWriteError : Error {
}

