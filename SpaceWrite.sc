
// Writes SpaceTracker files from their rendered soundfile form

SpaceWrite {
  
  var
    soundsInit,
    tree,
    linemap,
    
    // State is in object vars for ; other class methods can access it
    
    // Iteration state (used by soundsDo)
    lines,
    begins,
    ends,
    times,
    notes,
    sounds,
    polyphony,
    numChannels,
    consume,

    line,

    // First pass state
    index,
    overlap,
    previousOverlap,

    // Second pass state
    index,
    currentSectionBegin,
    nextSectionBegin,
    currentSectionParallel,
    previousSectionParallel,

    // Transfer state (used to convey information from the first to the second pass)
    <sections,
    <length,
    currentSectionIndex
  
  ;

  init {
  }

  *new {
    arg sounds,tree,linemap;
    ^super.newCopyArgs(sounds,tree,linemap).init;
  }

  soundsDo {
    arg action, merge = nil;

    var safety;

    safety = 0;

    polyphony = sounds.size;
    numChannels = sounds.first.numChannels;
    lines = Array.newClear(polyphony);
    notes = Array.newClear(polyphony);
    begins = Array.fill(polyphony, 0);
    ends = Array.fill(polyphony, 0);

    line = nil;

    sounds.do {|sound|sound.openRead};

    while {
      // Fill up a buffer of one line per polyphonic channel
      // (used to locate note ends and null notes)

      // as opposed to lines.size.do or lines.reverseDo, this
      // allows removeAt with correct indices

      lines.size.reverseDo {
        arg i;
        
        line = lines[i];

        // Make sure this element of the lines buffer is full, not nil
        // decrease buffer size if sound has exhausted

        // Line can be nil, when:
        // - just initialized
        // - consume and made note of pause

        // - consume and written to tree file
        if ( line.isNil ) {
          line = FloatArray.newClear(numChannels);
          sounds[i].readData(line);


          if (line.size == numChannels) {
            
            line = line.as(Array);
            
            switch (merge) {nil}{
              // noop
            }{\all} {
              this.merge(i);
            }{\pauses}{
              if (line[1] == 0) {
                this.merge(i);
              };
            };

            lines.put(i, line);
            
            notes.put(i, line[1]);

            ends.atInc(i, line[0]);

          }{
            length = ends.maxItem;
            sounds[i].close;
            sounds.removeAt(i);
            lines.removeAt(i);
            begins.removeAt(i);
            ends.removeAt(i);
            notes.removeAt(i);
          };
        };
      };
      
      // Termination when all soundfiles depleted
      lines.size > 0;
    }{

      consume = block { |consume|
        action.value(consume);
      };

      if (consume.isNumber == false) {
        SpaceWriteError("Must");
      };

      if (consume.notNil) {
        // Beginning and end of consume note are not the same.
        // End of consume note will increase again when received new
        // line from soundfile
        begins.atInc(consume, lines[consume][0]);

        lines.put(consume, nil);
      
        safety = 0;
      }{
        safety = safety + 1;
        if (safety > (polyphony * 4)) {
        SpaceWriteError("No note consumed over % times, internal error".format(polyphony*4)).throw;
        };
      };
    };
  }
  
  merge {
    arg i;

    // Merged mode turns all notes not separated by pauses
    // into one note, and all pauses not seperated by notes
    // into one pause.
    // This helps determining sections with simple criteria
    // (consume earliest end and compare line)
    while {
      var nextLine;
      nextLine = FloatArray.newClear(numChannels);
      sounds[i].readData(nextLine);
     
      if (nextLine.size == numChannels) {
        
        if ((nextLine[1] == 0) == (line[1] == 0)) {
          line[0] = line[0] + nextLine[0];
          true;
        }{
          sounds[i].seek(-1, 1);
          false;
        };
      }{
        false;
      };
    };
  }

  initFirstPass {
    index = nil;
    overlap = nil;
    previousOverlap = nil;
    sections = [];
    sounds = soundsInit.copy;
  }

  firstPass {
    var overlapAtLeastUntil = 0, numNotes = nil;
    this.soundsDo({|consume|

      index = ends.minIndex;
      numNotes = notes.select{|n| n != 0}.size;

      previousOverlap = overlap;
      overlap = (numNotes > 1);
      
      if (overlap) {
        overlapAtLeastUntil=ends.select{|d, i| notes[i]!=0}.maxItem;
      };

      if (overlapAtLeastUntil >= ends[index]) {
        overlap = true;
      };

      case { previousOverlap.isNil }{
        sections=sections.add(overlap).add(0);
      } { previousOverlap != overlap } {
        sections=sections.add(overlap);
        if (overlap) {
          sections=sections.add(begins.select{|d, i| notes[i]!=0}.minItem);
        }{
          if (numNotes == 0) {
            sections=sections.add(begins.maxItem);
          }{
            sections=sections.add(begins.select{|d, i| notes[i]!=0}.minItem);
          };
        };
      };

      consume.(index);
    }, merge: \pauses);
     
    //sections=sections.collect {|e|if(e.class==Float) {e.round(0.000001)}{ e }};
  }

  // Second pass submethods
  advanceSection {
    currentSectionIndex = currentSectionIndex + 2;
    previousSectionParallel = currentSectionParallel;
    currentSectionParallel = sections[currentSectionIndex];
    currentSectionBegin = sections[currentSectionIndex + 1];
    nextSectionBegin = sections[currentSectionIndex + 3] ?? 2147483647; // TODO: replace maxInt with song length
  }

  writeLine { |line, indent = 0|
    line = linemap.convertToSymbolic(line);
    tree.write(line, indent);
  }

  writePause { |length, indent = 0|
    this.writeLine(Array.fill(numChannels, 0).put(0, length), indent);
  }

  writePauseIfNotZero { |length, indent=0| 
    if (length > 0) {
      this.writeLine(Array.fill(numChannels, 0).put(0, length), indent);
    };
  }

  initSecondPass {
    sounds = soundsInit.copy;
    currentSectionIndex = -2;
    nextSectionBegin = 0;
  }

  secondPass {
    var lastEnd = 0, advance, rechannel, indent;
    this.soundsDo({ |consume|
       
      advance = begins.minItem.equalWithPrecision(nextSectionBegin);
      if (advance) {
        this.advanceSection;
        ([\advance, currentSectionParallel, nextSectionBegin, \begins]++begins++[\ends]++ends++[\lengths]++lines.collect{|l|l[0]}).postm;
      };
     
      if(currentSectionParallel == false) {
      
        ([\sequential, \lastEnd, lastEnd, \begins]++begins++[\ends]++ends++[\lengths]++lines.collect{|l|l[0]}).postm;
      
        if (advance) {
          lastEnd = currentSectionBegin;
        };

        notes.do {|n, i|
          if (n.equalWithPrecision(0) == false) {
            if (ends[i].equalWithPrecision(nextSectionBegin) || (ends[i] < nextSectionBegin)) {
              this.writeLine(lines[i], 0);
              lastEnd = ends[i];
              [\note, i].postm;
              consume.(i);
            }
          };
        };
        
        ends.do {|e, i|
          if (e > nextSectionBegin && (begins[i] < nextSectionBegin)) {
            [\endshorten, i].postm;
            begins[i] = nextSectionBegin;
            lines[i][0] = ends[i] - nextSectionBegin;
            consume.(nil);
          };
        };

        ends.do {|e, i|
          if (e < lastEnd || e.equalWithPrecision(lastEnd)) {
            [\drop, i].postm;
            consume.(i);
          };
        };

        index = ends.minIndex;
        begins[index] = lastEnd;
        lines[index][0] = ends[index] - lastEnd;
        this.writeLine(lines[index]);
        lastEnd = ends[index];
        [\shorten, index].postm;
        consume.(index);

      }{

        if (advance) {
          index = 0;
          this.writeLine(lines[index], 1);
          consume.(index);
        };
 
        if (ends[index].equalWithPrecision(nextSectionBegin)) {
          indent = if(begins[index].equalWithPrecision(currentSectionBegin), 1, 2);
          this.writeLine(lines[index], indent);
          index = index + 1;
          if (index >= lines.size) {
            SpaceWriteError("Index got too large, internal error");
          };
          consume.(index - 1);
        };
        
        if ((ends[index] > nextSectionBegin) && (begins[index] < nextSectionBegin) && (false == begins[index].equalWithPrecision(nextSectionBegin))) {
          indent = if(begins[index].equalWithPrecision(currentSectionBegin), 1, 2);
          this.writePause(nextSectionBegin - begins[index], indent);
          begins[index] = nextSectionBegin;
          lines[index][0] = ends[index] - nextSectionBegin;
          index = index + 1;
          if (index >= lines.size) {
            SpaceWriteError("Index got too large, internal error");
          };
          consume.(nil);
        };
 
        indent = if(begins[index].equalWithPrecision(currentSectionBegin), 1, 2);
        this.writeLine(lines[index], indent);
        consume.(index);
      };

    });

  }

  debugSecondPass {
    // Debug output, keep around
    [
      line[2], $ ,
      //if(index.isNil, if(this.nextNoteIsInNextSection, $o, $.), $-),
      switch(currentSectionParallel,nil,$|, true, $=, false, $-), $ ,
      \currentSectionBegin++$:++currentSectionBegin, $ ,
      \nextSectionBegin++$:++nextSectionBegin, $ ,
      'begins[index]'++$:++begins[index],$ ,
      \currentSectionParallel++$:++currentSectionParallel, $ , 
      //\nextNoteIsInNextSection++$:++this.nextNoteIsInNextSection, $  ,
      //begins,
      //ends,
    ].join.postm;
  }

  analyze {
    // First pass: Discover overlaps in sound files
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
//      debug.postm;
//    };


  }

  apply {

    // Second pass: Write to tree using information collected in first pass
    this.initSecondPass;
    this.secondPass;
  }
}

SpaceWriteError : Error {
}

