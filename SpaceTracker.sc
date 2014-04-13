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
    readClass,
    writeClass,
    linemapClass,
    soundClass
  ;

  var
    <>tree,
    <>linemap,
    <>polyphony,
    <>server,
    <>headerFormat="AIFF",
    <>sampleFormat="float",
    <>sounds
  ;

  *initClass {
    readClass = SpaceRead;
    linemapClass = SpaceLinemap;
    soundClass = SoundFile;
  }

  *new {
    arg treefile, polyphony = 8;
    ^super.newCopyArgs.init(treefile, polyphony);
  }

  init {
    | treefile |
    if (treefile.isNil) {
      ("treefile is required").throw;
    };

    tree = SpaceTree.new(treefile);

    linemap = linemapClass.new(treefile.splitext[1].asSymbol);

    server=Server.default;
  }

  *fromSoundFile {
    arg treefile, soundfile, force=false;
    ^this.new(treefile).fromSoundFile(soundfile, force);
  }

  fromSoundFile {
    arg soundfile, force = false;
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

  *toSoundFile {
    arg treefile, soundfile, force=false;
    ^this.new(treefile).toSoundFile(soundfile, force);
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

  read {
    arg soundfile, force = false;
   
    var numChannels;

    if (false == File.exists(tree.filename)) {
      (tree.filename + "does not exist").throw;
    };

    tree.parse({
      arg line;
      line = linemap.convertToNumeric(line);
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
      file = soundfile++if(i > 0, $.++i, "");
      if(File.exists(file) && (force == false)) { (file + "exists").throw };
      File.delete(file);
      if (false == sound.openWrite(file)) {
        ("Could not open"+file+"for writing").throw;
      };
      sounds.add(sound);
    });

    readClass.new(tree, sounds, linemap);

    ^readClass.toSounds;
  }

  write {
  
  
  }

}

