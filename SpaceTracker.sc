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

  fromSoundFile {
    arg soundfile, force = false;
    var sounds, tree, line, numChannels;
    
    if(File.exists(treefile) && (force == false)) { (treefile + "exists").throw };
    File.delete(treefile);

    if (false == File.exists(soundfile)) {
      (soundfile + "does not exist").throw;
    };
    
    sounds = List.new;

    tree = SpaceTree.new(treefile);

    block {
      var i, sound, file;
      i = 1;
      file = soundfile;
      while ({
        File.exists(file);
      }, {
        sound = soundClass.openRead(file);
        sounds.add(sound);
        file = soundfile ++ $. ++ i;
        i = i + 1;
      });
    };

    polyphony = sounds.size;
    
    numChannels = sounds[0].numChannels;

    block {
      var index, times, lines;

      index = 0;
      times = Array.fill(polyphony, 0);

      while ({sounds.size > 0}, {
        var sound, time, line;

        index = times.minIndex;
        sound = sounds[index];

        line = FloatArray.newClear(numChannels);
        sound.readData(line);
        //[index,sound.path,line].postln;
      
        if (line.size > 0, {
          time = line[0];
        
          times[index] = times[index] + time;
          
          line = this.format(line);
          tree.write(line);
        },{
          sound.close();
          sounds.removeAt(index);
          times.removeAt(index);
        });
        
      });
    };

    /*
    tree = SpaceTree.new(treefile);

    while ({
      line = FloatArray.newClear(numChannels);
      sound.readData(line);
      line.size > 0;
    }, {
      line = this.format(line);
      //tree.write(line, [1, 1,namingMapper.length]);
      tree.write(line);
    });

    sound.close;
    */
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
            
            if (indent < lastIndent) {
              ((lastIndent - indent) * 0.5).round.asInteger.do({
                indentTimes.pop;
              });
              indentTime = indentTimes.last;
            };
            index = times.minIndex;
            time = times[index];
            if (time > indentTime, {
              (this.class.name + "dropped note" + line).postln;
              continue.();
            });
          });
          
          //// Good, we figured out which channel we can use from
          //// indentation. Now insert the note.

          // Insert pre-pause if necessary
          // Parallel, so relative to indentTime when parallel started
          // Fill up with pause
          if (times[index] < indentTime) {
            sounds[index].writeData(FloatArray.newFrom([indentTime-times[index]]));
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
    note.postln;
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

