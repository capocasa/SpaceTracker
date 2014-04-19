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
    treeClass,
    readClass,
    writeClass,
    linemapClass,
    tmpClass,
    soundfileClass,
    defaultServer
  ;

  var
    <>polyphony,
    <>tree,
    <>linemap,
    <>numChannels,
    <>headerFormat="AIFF",
    <>sampleFormat="float",
    <>soundExtension="aif",
    <>server,
    <>sounds,
    <>tmp,
    <>read,
    <>soundfile
  ;

  *initClass {
    treeClass = SpaceTree;
    readClass = SpaceRead;
    linemapClass = SpaceLinemap;
    tmpClass = SpaceTmp;
    soundfileClass = SoundFile;
    defaultServer = Server.default;
  }

  *new {
    arg treefile, polyphony = 8;
    ^super.newCopyArgs(polyphony).init(treefile);
  }

  init {
    arg treefile;
    var ext, tmp;
    
    if (treefile.isNil) {
      ("treefile is required").throw;
    };

    tree = treeClass.new(treefile);

    linemap = linemapClass.new(treefile.splitext[1].asSymbol);

    tmp = tmpClass.new(16);

    server=defaultServer;
  }

  *toSoundFile {
    arg treefile, soundfile, force=false;
    ^this.new(treefile).toSoundFile(soundfile, force);
  }
  
  *toBuffer {
    arg treefile, action = false;
    ^this.new(treefile).toBuffer(treefile,action);
  }

  *fromSoundFile {
    arg treefile, soundfile, force=false;
    ^this.new(treefile).fromSoundFile(soundfile, force);
  }
  
  *fromBuffer {
    arg treefile, buffer, action;
    ^this.new(treefile).fromBuffer();
  }

  initChannels {
    tree.parse({
      arg line;
      line = linemap.convertToNumeric(line);
      numChannels = line.size;
      if (numChannels >1, \break, nil); // break only if not a pause
    });
  }

  soundFileName {
    arg channel;
    ^soundfile++if(channel > 0, $.++channel, "");
  }

  initSounds {
    sounds = Array.new(polyphony);
    
    polyphony.do({
      arg i;
      var sound, file;
      sound = soundfileClass.new
        .headerFormat_(headerFormat)
        .sampleFormat_(sampleFormat)
        .numChannels_(numChannels);
      file = this.soundFileName(i);
      File.delete(file);
      if (false == sound.openWrite(file)) {
        ("Could not open"+file+"for writing").throw;
      };
      sounds.add(sound);
    });
  
  }

  validateTreeRead {
    if (false == File.exists(tree.filename)) {
      (tree.filename + "does not exist").throw;
    };
  }

  validateSoundWrite {
    polyphony.do({
      arg i;
      var file;
      file = this.soundFileName(i);
      if (File.exists(file)) {
        (file + "exists, use 'force' to overwrite").throw
      };
    });
  }

  toSoundFile {
    arg arg_soundfile, force = false;
   
    soundfile = arg_soundfile;

    this.validateTreeRead;
    if (false == force) {
      this.validateSoundWrite;
    };

    this.initChannels;
    this.initSounds;

    read = readClass.new(tree, sounds, linemap);
    read.toSounds;
  }

  toBuffer {
    arg action = false;
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

  fromSoundFile {
    arg treefile, soundfile, force = false;
    ^this.class.new(treefile).fromSoundFile(soundfile, force);
  }

  fromBuffer {
    arg buffer, action;
    var soundfile, tracker;
    soundfile = tmp.file(soundExtension);
    buffer.write(soundfile, headerFormat, sampleFormat, -1, 0, false, {
      this.fromSoundFile(soundfile);
      action.value(this);
    });
    ^tracker;
  }
}

