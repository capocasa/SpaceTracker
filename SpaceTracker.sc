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
    if (false == File.exists(treefile)) {
      (treefile + "does not exist").throw;
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
    var sound, tree, line, numChannels;
    
    if(File.exists(treefile) && (force == false)) { (treefile + "exists").throw };
    File.delete(treefile);
    
    sound = soundClass.new;
    sound.openRead(soundfile);
    
    numChannels = sound.numChannels;
    
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
    var space, sounds, totaltimes, numChannels;

    if (soundfile.isNil) {
      soundfile = this.tmpFileName;
    };
    
    space = treeClass.new(treefile);
    
    space.parse({
      arg line;
      line = this.unformat(line);
      numChannels = line.size;
      if (numChannels >1, \break, nil); // break only if not a pause
    });
    
    sounds = Array.new(polyphony);
    polyphony.do({
      arg i;
      var sound, file;
      sound = soundClass.new
        .headerFormat_(headerFormat)
        .sampleFormat_(sampleFormat)
        .numChannels_(numChannels);
      file = soundfile++$.++i;
      if(File.exists(file) && (force == false)) { (file + "exists").throw };
      File.delete(file);
      if (false == sound.openWrite(file)) {
        ("Could not open"+file+"for writing").throw;
      };
      sounds.add(sound);
    });

    block {
      arg break_outer;
      var times, index, maxtime, maxtimes, in_use;
      
      times = Array.fill(polyphony, 0);
      in_use = List.new.add(0);
      maxtimes = List.new.add(0);
      
      space.parse({
        arg line, indent, lastindent;
        if (line.notNil) {
        block {
          arg break_inner;
          
          if (indent % 2 == 1, {
            
            // Odd indent does parallelization, so we figure out
            // which one to use
            
            var i;
            
            // fresh indent, note latest position
            
            // NOTE: this block is fishy, especially the difference thing below
            // mixing indices and values
            
            if (indent > lastindent) {
              maxtimes.add(times.maxItem);
            };
            
            i = times.difference(in_use).minItem;
            if (times[i] <= maxtimes.last, {
              in_use.add(i);
            },{
              (this.class.name + "dropped note" + line).postln;
              break_inner.value;
            });
          
          
          },{
            // Even indent is piling on more notes linearly
            // onto one parallization, so continue with the current indent
          
            // Handle de-indent
            if (indent < lastindent) {
              maxtimes.pop;
              in_use.pop;
            };
          
          });
          
          //// Good, we figured out which channel to use from
          //// indentation. Now insert the note.

          index = in_use.last;
          maxtime = maxtimes.last;
[in_use,maxtimes].postln;
          // Insert pre-pause if necessary
          // Parallel, so relative to max time when parallel started
          // Fill up with pause
          if (times[index] < maxtime) {
            sounds[index].write(FloatArray.newFrom([maxtime-times[index]]));
            times[index] = maxtime;
          };
          // Insert main line
          line = this.unformat(line);
          sounds[index].writeData(line);
          times[index] = times[index] + line[0];
        };
        };
      });
    };

    sounds.do({
      arg sound;
      sound.close;
    });
    ^soundfile;
  }

  toBuffer {
    arg action;
    var soundfile;
    soundfile = this.toSoundFile;
    ^Buffer.read(server, soundfile, 0, -1, action);
  }

  /* These are not part of the public interace and might change */

  format {
    arg line;
    var time, divisor, note;
  
    line = Array.newFrom(line);
    
    // For note length, just make everything specified
    // in quarter notes. This could be made more powerful later.
    line = line.insert(1, defaultDivisor);

    note = line[2];
    note = this.formatNote(note);
    line[2] = note;

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

