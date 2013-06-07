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
    <>soundClass,
    chunksize = 1024
  ;

  var
    <>treefile,
    <>server,
    <>headerFormat="AIFF",
    <>sampleFormat="float",
    <>polyphony = 4,
    <>maxnote = 7, // 7th power of 2 is 128, so shortest note specified by integer is 1/128th
    <>namingClasses = IdentityDictionary[
      \note -> NamingNote,
      \drum -> NamingDrum
    ];
  ;

  *new {
    arg treefile;
    ^super.newCopyArgs(treefile).init;
  }

  *initClass {

    mappers = IdentityDictionary.new;

    };
  
    treeClass = SpaceTree;
    soundClass = SoundFile;
  }

  init {
    server=Server.default;
    naming = treefile.splitext[1].asSymbol;
    namingMapper = namingClasses.at(naming).new;
  }

  *fromSoundFile {
    arg treefile, soundfile;
    ^this.new(treefile).fromSoundFile(soundfile);
  }

  fromSoundFile {
    arg soundfile;
    var sound, tracker, tree, line, samples, numChannels, cs, frame;
    sound = soundClass.new;
    sound.openRead(soundfile);
    
    if(File.exists(treefile)) { (treefile + "exists").throw };
    
    numChannels = sound.numChannels;
    
    if (numChannels % polyphony != 0) {
      "Sound file channels must be a multiple of polyphony".throw;
    };

    frame = numChannels / polyphony;

    cs = chunksize - (chunksize % numChannels);
    
    tree = SpaceTree.new(treefile);

    samples = FloatArray.newClear(cs);

    while ({
      sound.readData(samples);
      samples.size > 0;
    }, {
      var i, j, size;
      i=0;
      j=0;
      size = samples.size;
      while ( { i < size }, {
        j = i + frame - 1;
        line = samples.copyRange(i.asInteger, j.asInteger);
        line = this.format(line);
        tree.write(line, [3,namingMapper.length]);
        i = j+1;
      });
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
    arg soundfile;
    var space, sound, tmp, chunk, counter, numChannels, frame, cs;

    if (soundfile.isNil) {
      soundfile = this.tmpFileName;
    };

    space = treeClass.new(treefile);

    space.parse({
      arg line;
      frame = line.size;
      \break;
    });

    numChannels = polyphony * frame;

    sound = soundClass.new
      .headerFormat_(headerFormat)
      .sampleFormat_(sampleFormat)
      .numChannels_(numChannels);
    sound.openWrite(soundfile);
    
    cs = chunksize - (chunksize % numChannels);
    chunk = FloatArray.new(cs);

    space.parse({
      arg line, indent, lastindent;
      if (line.notNil) {
        line = this.unformat(line);
        for (0, frame-1, {
          arg i;
          chunk.add(line[i]);
        });
      };
      if (chunk.size == cs) {
        sound.writeData(chunk);
        chunk = FloatArray.new(cs);
      };
    });
    sound.writeData(chunk);

    sound.close;
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
    arg samples;
    var time, note, line;
  
    line = Array.newFrom(samples);

    time = line[0];
    note = line[1];

    time = this.formatTime(time);
    note = this.formatNote(note);
  
    line[0] = time;
    line[1] = note;
    ^line;
  }
  
  unformat {
    arg line;

    var
      time,
      note
    ;

    time = line[0];
    time = this.unformatTime(time);

    note = line[1];
    note = this.unformatNote(note);

    line[0] = time;
    line[1] = note;

    ^FloatArray.newFrom(line);
  }
  
  formatTime {
    arg time;
    var attempt;
    attempt = 1;
    block {
      arg break;
      for (1, maxnote, {
        attempt = attempt * 2;
        time = time * 2;
        if (time.asInteger==time) {
          break.value;
        };
      });
    };
    if (attempt > 1) {
      // if time is a multiple of 2, interpret as note (4 = quarter note, etc)
      time = attempt;
    };
    ^time;
  }
  
  unformatTime {
    arg time;
  
  ^case
      { time.class == Integer } { 1/time  }
      { time }
    ;
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

