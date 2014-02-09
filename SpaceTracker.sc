/*

SpaceTracker

A tracker-style file format for programming melodies and rhythms.

Uses indenting to allow polyphony. Is gridless, allowing for
staccato and arbitrary rhythms.

The name is an homage to 70s counterculture. It is designed
to be simple enough to write even while spaced out, which can
only be good for the artistic quality of music written with it.

*/

SpaceTracker {

  classvar
    <>naming,
    <>lengths,
    <>treeClass,
    <>soundClass
  ;

  var
    <>treefile,
    <>server,
    <>headerFormat="AIFF",
    <>sampleFormat="float",
    <>polyphony = 8,
    <>namingMapper,
    <>defaultDivisor = 4,
    <>zeroNote = 0 // avoid magic number
  ;
  classvar
    <>namingClasses
  ;

  *new {
    arg treefile;
    ^super.newCopyArgs(treefile).init;
  }

  *initClass {

    treeClass = SpaceTree;
    soundClass = SoundFile;
    
    namingClasses = IdentityDictionary[
      \note -> NamingNote,
      \drum -> NamingDrum
    ];
  }

  init {
    if (treefile.isNil) {
      ("treefile is required").throw;
    };
    server=Server.default;
    naming = treefile.splitext[1].asSymbol;
    namingMapper = namingClasses.at(naming).new;
  }

  *fromSoundFile {
    arg treefile, soundfile, force=false;
    ^this.new(treefile).fromSoundFile(soundfile, force);
  }

  openSoundFiles {
    arg soundfile;
    var sounds;
    sounds = List.new;
    
    if (false == File.exists(soundfile)) {
      (soundfile + "does not exist").throw;
    };
    block {
      var i, sound, file;
      i = 0;
      file = soundfile;
      while ({
        File.exists(file);
      }, {
        sound = soundClass.openRead(file);
        sounds.add(sound);
        //sources.add(i);
        i = i + 1;
        file = soundfile ++ $. ++ i;
      });
    };
    ^sounds;
  }

  soundFilesDo {
    arg soundfile,callback;
    var
      sounds,
      lines,
      begins,
      ends,
      delta,
      polyphony,
      numChannels,
      consumed
    ;
    
    sounds = this.openSoundFiles(soundfile);
    polyphony = sounds.size;
    numChannels = sounds[0].numChannels;
    lines = Array.newClear(polyphony);
    begins = Array.fill(polyphony, 0);
    ends = Array.fill(polyphony, 0);
    delta = Array.fill(polyphony, 0);
      
    block {
      arg break;
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
          break.();
        };

        consumed = callback.(lines,begins,ends);

        // Beginning and end of consumed note are not the same.
        // End of consumed note will increase again when received new
        // line from soundfile
        begins.atInc(consumed, lines.at(consumed).at(0));

        lines.put(consumed, nil);

      });
    }
  }

  fromSoundFile {
    arg soundfile, force = false;
    var tree, numChannels;
    
    if(File.exists(treefile) && (force == false)) { (treefile + "exists").throw };
    File.delete(treefile);
    
    tree = SpaceTree.new(treefile);


    block {
      // Variables that persist through each iteration
      var
        previousOverlap,
        latestEnd,
        previousEnd
      ;
        
      // Initialize
      previousOverlap = false;
      latestEnd = 0;
      previousEnd = 0; 
      
      this.soundFilesDo(soundfile, {
        arg lines,begins,ends;
        // Variables that get re-assigned for every iteration
        var
          notes,
          times,
          drop,
          isNote,
          index,
          overlapBackward,
          overlapForward,
          overlap,
          sectionChange
        ;

        // Default values
        overlap = false;
        overlapBackward = false;
        overlapForward = false;

        // Let's get started!

        // Loop until all lines from all sound files have been consumed
            
        notes = lines.collect({arg line; line[1]});
        times = lines.collect({arg line; line[0]});
        isNote = notes.collect({arg note, i; note != 0 });
        
        drop = isNote.indexOf(false);
        if (drop.isNil, {
          //if (false == overlap) {
            index = begins.minIndex;
          ////};
        },{
          index = drop;
        });
       // 
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
          sectionChange = 0;
          if (overlap && (false == previousOverlap)) {
            sectionChange = 1;
          };
          
          if ((false == overlap) && previousOverlap) {
            sectionChange = -1;
          };
          
          previousEnd = ends[index];
        });
        
        // Debug
        [
          switch(sectionChange, -1, "<", 0, " ", 1, ">"),
          if(overlap, "8", "o"),
          //if(previousOverlap, \previousOverlap, \nopreviousOverlap),
          if(overlapBackward, ":", "."),
          if(overlapForward, "=", "-"),
          begin: begins[index],
          end: ends[index],
          note: this.formatNote(notes[index]),
          index: index
          //time: times[index]
        ].postln;
        
        previousOverlap = overlap;
        
        // Return value marks consumed
        index;
      });
    };
  
  }

  *fromBuffer {
    arg treefile, buffer, action, naming;
    ^this.class.new(treefile).naming_(naming).fromBuffer;
  }

  fromBuffer {
    arg treefile, buffer, action;
    var soundfile, tracker;
    soundfile = this.tmpFileName;
    tracker = this.class.new(treefile);
    buffer.write(soundfile, headerFormat, sampleFormat, -1, 0, false, {
      tracker.fromSoundFile(soundfile);
      action.value(tracker);
    });
    ^tracker;
  }

  toSoundFile {
    arg soundfile, force = false;
    var space, sounds, totaltimes, numChannels, soundfiles;

    if (soundfile.isNil) {
      soundfile = this.tmpFileName;
    };
    
    if (false == File.exists(treefile)) {
      (treefile + "does not exist").throw;
    };
    
    space = treeClass.new(treefile);
    
    space.parse({
      arg line;
      line = this.unformat(line);
      numChannels = line.size;
      if (numChannels >1, \break, nil); // break only if not a pause
    });
    
    sounds = Array.new(polyphony);
    soundfiles = Array.new(polyphony);
    polyphony.do({
      arg i;
      var sound, file;
      sound = soundClass.new
        .headerFormat_(headerFormat)
        .sampleFormat_(sampleFormat)
        .numChannels_(numChannels);
      file = soundfile++if(i > 0, $.++i, "");
      if(File.exists(file) && (force == false)) { (file + "exists").throw };
      File.delete(file);
      if (false == sound.openWrite(file)) {
        ("Could not open"+file+"for writing").throw;
      };
      sounds.add(sound);
      soundfiles.add(file);
    });

    block {
      arg break;
      var index, time, times, indentTime, indentTimes;
      
      index = 0;
      time = 0;
      indentTime = 0;
      times = Array.fill(polyphony, 0);
      indentTimes = List.new.add(0);
      
      space.parse({
        arg line, indent, lastIndent;

        // [indent, lastIndent,((lastIndent - indent).abs * 0.5).round,indentTimes].postln;
        
        block {
          arg continue;
          if (line.isNil) {
            continue.();
          };
          if (indent % 2 == 1, {
            
            // Odd indent does parallelization, so we figure out
            // which channel to use
            
            // Keep track of indentTime by indent level
            // No note of a higher indent can come sooner than this
            if (indent > lastIndent) {
              var num;
              indentTime = times.maxItem;
              ((indent - lastIndent) * 0.5).round.asInteger.do({
                indentTimes.add(indentTime);
              });
            };
            
            index = times.minIndex;
            time = times[index];
            if (time > indentTime, {
              (this.class.name + "dropped note" + line).postln;
              continue.();
            });
          });

          if (indent % 2 == 0, {
            if (indent < lastIndent) {
              ((lastIndent - indent) * 0.5).round.asInteger.do({
                indentTimes.pop;
              });
              indentTime = indentTimes.last;
            };
          });
          
          //// Good, we figured out which channel we can use from
          //// indentation. Now insert the note.

          // Insert pre-pause if necessary
          // Parallel, so relative to indentTime when parallel started
          // Fill up with pause
          if (times[index] < indentTime) {
            sounds[index].writeData(FloatArray.fill(numChannels, 0).put(0, indentTime-times[index]));
            times[index] = indentTime;
          };
          // Insert main line
          line = this.unformat(line);
          sounds[index].writeData(line);
          times[index] = times[index] + line[0];
          
          // Must keep this debug line!
          // [index,line,times].postln;
        };
      });
    };

    sounds.do({
      arg sound;
      sound.close;
    });
    ^if(polyphony==1, soundfile, soundfiles);
  }

  toBuffer {
    arg action, force = false;
    var soundfile;
    soundfile = this.toSoundFile(nil, true);
    if (Array == soundfile.class, {  
      ^soundfile.collect({
        arg file;
        Buffer.read(server, file, 0, -1, action);
      });
    }, {
      ^Buffer.read(server, soundfile, 0, -1, action);
    });
  }

  /* These are not part of the public interace and might change */

  format {
    arg line;
    var time, divisor, note;
  
    line = Array.newFrom(line);
   
    time = line[0];
    note = line[1];
    
    // For note length, just make everything specified
    // in quarter notes. This could be made more powerful later.
    divisor = if(time == 0, 0, defaultDivisor);
    
    note = this.formatNote(note);
    time = time * divisor;

    line[0] = time;
    line[1] = note;
    
    line = line.insert(1, divisor);

    if (line.occurrencesOf(0) == line.size) {
      line = [0]; // Syntactic sugar: null line is a single zero
    };

    ^line;
  }
  
  unformat {
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

    // Detect note format
    time = line[0];
    divisor = line[1];
    
    // First two numbers are integers - assume "note" style line
    // So calculate time float from first two numbers, and shorten
    // the line
    time = this.unformatTime(time, divisor);
    note = line[2];

    line.removeAt(1);
    
    note = this.unformatNote(note);

    line[0] = time;
    line[1] = note;

    for(0, line.size-1, {
      arg i;
      line[i] = (line[i] ? 0) .asFloat;
    });

    ^FloatArray.newFrom(line);
  }

  unformatTime {
    arg time, divisor;
    ^time / divisor;
  }

  formatNote {
    arg note;
    ^namingMapper.string(note);
  }

  unformatNote {
    arg note;
    ^namingMapper.number(note);
  }

  tmpName {
    arg length = 12;
    ^"abcdefghijklmnopqrstuvwxyz".scramble.copyRange(0,12);
  }

  tmpFileName {
    ^Platform.defaultTempDir +/+ this.tmpName ++ $. ++ headerFormat.toLower;
  }
}

