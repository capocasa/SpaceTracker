
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
    previousEnd,
    previousType,
    
    // First pass reassign (gets reassigned after each iteration)
    isNote,
    index,
    overlapBackward,
    overlapForward,
    overlap,
    sectionParallel,
    type,
    
    // Second pass state (re-used commented out)
    // index,
    // sectionParallel,
    begin,
    sectionBegin,
    parallelCount,
    
    // Second pass reassign
    line,
    indent,
    sectionParallel,
  
    // Shared state
    sections,            // This contains the information the first pass gleans for the second
  
    // Iteration state
    lines,
    begins,
    ends,
    notes,
    times,
    drop
  ;

  init {
    sections = List.new; 
  }

  *new {
    arg sounds,tree,linemap;
    ^super.newCopyArgs(sounds,tree,linemap).init;
  }

  soundsDo {
    arg action;
    var
      delta,
      polyphony,
      numChannels,
      consumed,
      times,
      isNote
    ;
    
    polyphony = sounds.size;
    numChannels = sounds[0].numChannels;
    lines = Array.newClear(polyphony);
    begins = Array.fill(polyphony, 0);
    ends = Array.fill(polyphony, 0);
    delta = Array.fill(polyphony, 0);
      
    while ({true}, {
      // Fill up a buffer of one line per polyphonic channel
      // (used to locate note ends and null notes)

      // as opposed to lines.size.do or lines.reverseDo, this
      // allows removeAt with correct indices
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
            
            delta.put(i, line.at(0));
            
            ends.atInc(i, delta.at(i));
            
          },{
            sounds.removeAt(i);
            lines.removeAt(i);
            begins.removeAt(i);
            ends.removeAt(i);
            delta.removeAt(i);
          });
        };
      });
    
      // Termination when all soundfiles depleted
      if (lines.size == 0) {
        ^this;
      };
      
      notes = lines.collect({arg line; line[1]});
      times = lines.collect({arg line; line[0]});
      isNote = notes.collect({arg note, i; note != 0 });
      
      drop = isNote.indexOf(false);

      consumed = action.value;

      if (consumed.isNil) {
        "Please return the index to consume".throw;
      };

      // Beginning and end of consumed note are not the same.
      // End of consumed note will increase again when received new
      // line from soundfile
      begins.atInc(consumed, lines.at(consumed).at(0));

      lines.put(consumed, nil);

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
    previousOverlap=false;
    latestEnd=0;
    previousEnd=0;
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
      if (drop.isNil, {
        index = begins.minIndex;
      },{
        index = drop;
      });
      
      if (drop.isNil, {
        // detect overlap
        overlapBackward = previousEnd > begins[index];
        
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
        sectionParallel = nil;
        if (overlap && (false == previousOverlap)) {
          sectionParallel = true;
        };
        
        if ((false == overlap) && previousOverlap) {
          sectionParallel = false;
        };
        
        previousEnd = ends[index];
      });
      
      // Debug
      
      // Keep this debug output around, it's the bread
      // and butter of developing this algorithm more easily
      /*
      [
        switch(sectionParallel, false, "<", nil, " ", true, ">"),
        if(overlap, "8", "o"),
        //if(previousOverlap, \previousOverlap, \nopreviousOverlap),
        if(overlapBackward, ":", "."),
        if(overlapForward, "=", "-"),
        begin: begins[index],
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
      if (sectionParallel.notNil, {
        sections.add(sectionParallel);
        sections.add(begins[index]);
      });

      // Lookbehind
      previousOverlap = overlap;

      // Return value marks consumed
      index;
    });
  }

  initSecondPass {
    index = 0;
    sectionParallel = false;
    begin = 0;
    sectionBegin = 0;
    parallelCount = 0;
  }

  // Second pass submethods

  drop {
    index = drop;
    indent = 0;
  }

  isChanged {
    ^ends.at(index) >= sectionBegin && sections.size > 0;
  }

  consumeChanges {
    sectionParallel = sections.removeAt(0);
    sectionBegin =  sections.removeAt(0);
  }

  changedParallel {
    index = begins.minIndex;
    
    // Go through all parallels of this section
    if (parallelCount == lines.size, {
      this.consumeChanges;
      parallelCount = 0;
    }, {
      parallelCount = parallelCount + 1;
    });
    indent = 1;
  }

  changedNotParallel {
    this.consumeChanges;
    index = begins.minIndex;
    indent = 0;
  }

  notChangedParallel {
    if (indent != 2, {
      indent = 2;
    });
  }

  notChangedNotParallel {
    index = begins.minIndex;
    indent = 0;
  }

  prepareLine {
    line = lines[index];
    line = linemap.convertToSymbolic(line);
  }

  writeLine {
    tree.write(line, indent);
  }

  secondPass {

    sections.postln;

    this.soundsDo({ 

      if (drop.notNil, {
        this.drop;
      },{
        
        // Debug output, keep around
        [
          if(sectionParallel,\parallel, \sequential),
          if(this.isChanged, \changed, \remained),
          \sectionBegin, sectionBegin,
          \indexEnd, ends.at(index)
        ].postln;
        
        
        if (this.isChanged, {
          if (sectionParallel, {
            this.changedParallel;
          },{
            this.changedNotParallel;
          });
        },{
          if(sectionParallel, {
            this.notChangedParallel;
          },{
            this.notChangedNotParallel;
          });
        });

        this.prepareLine;
        this.writeLine;
      });

      index;
    });
  }

  fromNumeric {
    
    // First pass: Discover overlaps in sound files
    this.resetSounds;
    this.initFirstPass;
    this.firstPass;

    // Second pass: Write to tree using information collected in first pass
    this.resetSounds;
    this.initSecondPass;
    this.secondPass;
  }
}

