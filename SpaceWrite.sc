
// Writes SpaceTracker files from their rendered soundfile form

SpaceWrite {
  
  var
    sounds,
    tree,
    linemap,
    
    // State is in object vars for precedurality; other class methods can access it
    
    // Iteration state (used by soundsDo)
    lines,
    begins,
    ends,
    times,
    notes,
    polyphony,
    numChannels,
    consume,

    // First pass state
    index,
    overlap,
    previousOverlap,

    // Second pass state
    index,
    currentNoteEnd,
    currentSectionBegin,
    nextSectionBegin,
    parallelGroupIndex,
    currentSectionParallel,
    previousNoteEnd,
    previousType,
    previousEnd,

    // Second pass reassign
    line,
    indent,

    // Transfer state (used to convey information from the first to the second pass)
    <sections,
    <length,
    currentSectionIndex,
  
    notesWrittenInSection
  ;

  init {
  }

  *new {
    arg sounds,tree,linemap;
    ^super.newCopyArgs(sounds,tree,linemap).init;
  }

  soundsDo {
    arg action, merge = nil;

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
        action.value(consume, line);
      };

      if (consume.notNil) {
        // Beginning and end of consume note are not the same.
        // End of consume note will increase again when received new
        // line from soundfile
        begins.atInc(consume, lines.at(consume).at(0));

        lines.put(consume, nil);
      };
    };

    sounds.do {|sound|sound.close;};
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
  
    sections=sections.collect {|e|if(e.class==Float) {e.round(0.000001)}{ e }};
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
    notesWrittenInSection = notesWrittenInSection + 1;
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
    notesWrittenInSection = 0;
  }

  initSecondPass {
    currentSectionIndex = -2;
    this.advanceSection;

    currentNoteEnd = 0;
    previousNoteEnd = 0;
  }

  writeBreakIfRequired {
    var storeIndent;
    if (notesWrittenInSection == 0 && currentSectionParallel && (sections[currentSectionIndex-2] ?? false)) {
      storeIndent = indent;
      indent = 0;
      line = [0];
      this.writeLine;
      indent = storeIndent;
    };
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
      //begins,
      //ends,
    ].join.postln;
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
//      debug.postln;
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

