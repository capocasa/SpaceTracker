
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
//        [\naturalOverlap, ends[index], overlapAtLeastUntil].postm;
        overlapAtLeastUntil=ends.select{|d, i| notes[i]!=0}.maxItem;
      };

      if (overlap == false && ((overlapAtLeastUntil > ends[index]) || overlapAtLeastUntil.equalWithPrecision(ends[index]))) {
//        [\forcedOverlap, ends[index], overlapAtLeastUntil].postm;
        overlap = true;
      };

      if (overlap == false) {
//        [\noOverlap, ends[index], overlapAtLeastUntil].postm;
      };

      case { previousOverlap.isNil }{
        sections=sections.add(overlap).add(0);
      } { previousOverlap != overlap } {
        sections=sections.add(overlap);
        if (overlap) {
          sections=sections.add(begins.select{|d, i| notes[i]!=0}.minItem);
        }{
          if (numNotes == 0) {
            sections=sections.add(begins.maxItem.max(overlapAtLeastUntil));
          }{
            sections=sections.add(begins.select{|d, i| notes[i]!=0}.minItem.max(overlapAtLeastUntil));
          };
        };
      };

      consume.(index);
    }, merge: \pauses);

    //sections=sections.collect {|e|if(e.class==Float) {e.round(0.000001)}{ e }};
  }

  // remove sections with identical start times (seems to only happen false->true)
  dedup {
    var kill = List[];
    sections.pairsDo { |p, t, i|
      var p1 = sections[i+2], t1=sections[i+3];
      if (t1.notNil) {
        if(p.not && p1 && t1.equalWithPrecision(t)) {
          kill.add(i);
        };
      };
    };
    kill.reverseDo { |i|
      sections.removeAt(i+1);
      sections.removeAt(i);
    };
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
       
      advance = begins.minItem > nextSectionBegin || begins.minItem.equalWithPrecision(nextSectionBegin);
      if (advance) {
        this.advanceSection;
        ([\advance, currentSectionParallel, nextSectionBegin, \begins]++begins++[\ends]++ends++[\lengths]++lines.collect{|l|l[0]}).postm;
        if(previousSectionParallel == true && currentSectionParallel == true) {
          this.writePause(0, 0);
        };
      };
     
      if(currentSectionParallel == false) {
      
        ([\sequential, \lastEnd, lastEnd, \begins]++begins++[\ends]++ends++[\lengths]++lines.collect{|l|l[0]}).postm;
      
        if (advance) {
          lastEnd = currentSectionBegin;
        };

        // Preferentially consume notes
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

        // Drop pauses that end before the latest note does
        ends.do {|e, i|
          if (e < lastEnd || e.equalWithPrecision(lastEnd)) {
            [\drop, i].postm;
            consume.(i);
          };
        };

        // Shorten pauses to the end of the latest note
        index = ends.minIndex;

        if (ends[index] < nextSectionBegin) {
          lines[index][0] = ends[index] - lastEnd;
          this.writePause(lines[index][0]);
          lastEnd = ends[index];
          [\shorten, index].postm;
          consume.(index);
        };
        
        index = begins.minIndex;
        
        lines[index][0] = ends[index] - nextSectionBegin;
        begins[index] = nextSectionBegin;
        this.writePauseIfNotZero(nextSectionBegin - lastEnd);
        lastEnd = nextSectionBegin;
        [\endShorten, index].postm;
        consume.(nil);
      }{
        
        ([\parallel, \nextSectionBegin, nextSectionBegin, \begins]++begins++[\ends]++ends++[\lengths]++lines.collect{|l|l[0]}).postm;

        if (advance) {
          index = 0;
          ([\parallelAdvance, lines[index][0]]).postm;
          this.writeLine(lines[index], 1);
          consume.(index);
        };

        if (index >= lines.size) {
          [\depleteReindex, index].postm;
          index = lines.size-1;
        };


        if (ends[index].equalWithPrecision(nextSectionBegin)) {
          indent = if(begins[index].equalWithPrecision(currentSectionBegin), 1, 2);
          ([\parallelReindex, lines[index][0]]).postm;
          this.writeLine(lines[index], indent);
          index = index + 1;
          consume.(index - 1);
        };
        
        if ((ends[index] > nextSectionBegin) && (begins[index] < nextSectionBegin) && (false == begins[index].equalWithPrecision(nextSectionBegin))) {
          indent = if(begins[index].equalWithPrecision(currentSectionBegin), 1, 2);
          ([\parallelShorten, lines[index][0]]).postm;
          this.writePause(nextSectionBegin - begins[index], indent);
          begins[index] = nextSectionBegin;
          lines[index][0] = ends[index] - nextSectionBegin;
          index = index + 1;
          consume.(nil);
        };
        
        if ((ends[index] > nextSectionBegin || ends[index].equalWithPrecision(nextSectionBegin)) && ((begins[index] > nextSectionBegin) || (begins[index].equalWithPrecision(nextSectionBegin)))) {
          ([\parallelNilReindex, lines[index][0]]).postm;
          index = index + 1;
          consume.(nil);
        };
 
        indent = if(begins[index].equalWithPrecision(currentSectionBegin), 1, 2);
        ([\parallelWrite, lines[index][0]]).postm;
        this.writeLine(lines[index], indent);
        consume.(index);
      };

    });

  }

  analyze {
    // First pass: Discover overlaps in sound files
    this.initFirstPass;
    this.firstPass;
    this.dedup;
  }

  apply {

    // Second pass: Write to tree using information collected in first pass
    this.initSecondPass;
    this.secondPass;
  }
}

SpaceWriteError : Error {
}

