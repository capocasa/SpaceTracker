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
    <>polyphony = 1,
    <>namingMapper,
    <>shortestnote = 128 // The shortest note to look for is a 128th note
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
    var sound, tracker, tree, line, samples, numChannels, cs, frame;
    
    if(File.exists(treefile) && (force == false)) { (treefile + "exists").throw };
    File.delete(treefile);
    
    sound = soundClass.new;
    sound.openRead(soundfile);
    
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
        //tree.write(line, [1, 1,namingMapper.length]);
        tree.write(line);
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
    arg soundfile, force = false;
    var space, sound, tmp, chunk, counter, numChannels, frame, cs;

    if (soundfile.isNil) {
      soundfile = this.tmpFileName;
    };

    if(File.exists(soundfile) && (force == false)) { (soundfile + "exists").throw };
    File.delete(soundfile);

    space = treeClass.new(treefile);

    space.parse({
      arg line;
      line = this.unformat(line);
      frame = line.size;
      if (frame>1, \break, nil); // break only if not a pause
    });
    
    numChannels = polyphony * frame;
    
    sound = soundClass.new
      .headerFormat_(headerFormat)
      .sampleFormat_(sampleFormat)
      .numChannels_(numChannels);
    if (false == sound.openWrite(soundfile)) {
      ("Could not open"+soundfile+"for writing").throw;
    };
    
    cs = chunksize - (chunksize % numChannels);
    chunk = FloatArray.new(cs);

    space.parse({
      arg line, indent, lastindent;
      line.postln;
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
    var time, divisor, note, line;
  
    line = Array.newFrom(samples);

    time = line[0];
    note = line[1];
    
    note = this.formatNote(note);
    line[1] = note;

    time = this.formatTime(time);
    
    if (time.isArray) {
      divisor = time[1];
      time = time[0];
      line[0] = 2 ** divisor;
      line.addFirst(nil);
    };

    line[0] = time;

    ^line;
  }
  
  unformat {
    arg line;

    var
      time,
      divisor,
      note
    ;

    // Detect note format

    time = line[0];
    divisor = line[1];
    
    if (time.asInteger == time && divisor.asInteger == divisor, {
      // First two numbers are integers - assume "note" style line
      // So calculate time float from first two numbers, and shorten
      // the line
      time = this.unformatTime(time, divisor);
      note = line[2];

      line.removeAt(0);
    },{
      time = time.asFloat;
      note = line[1];
    });
    
    note = this.unformatNote(note);

    line[0] = time;
    line[1] = note;
    
    ^FloatArray.newFrom(line);
  }
  
  // If the time is a float that is a fraction of 2, transform
  // to note format (two integers), otherwise return the fraction
  formatTime {
    arg time;
    var t = time, d = 1;
    while ( {t != t.asInteger }) {
      d = d * 2;
      if (d > shortestnote) {
        ^time;
      };
      t = t * 2;
    };
  
    ^[t, d]
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

