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
    >treefile,
    <>soundfile,
    <>linemap,
    <>tree,
    <>polyphony,
    <>numChannels,
    <>headerFormat="AIFF",
    <>sampleFormat="float",
    <>soundExtension="aif",
    <>server,
    <>sounds,
    <>tmp,
    <>read,
    <>write
  ;

  *initClass {
    treeClass = SpaceTree;
    readClass = SpaceRead;
    writeClass = SpaceWrite;
    linemapClass = SpaceLinemap;
    tmpClass = SpaceTmp;
    soundfileClass = SoundFile;
    defaultServer = Server.default;
  }

  *new {
    arg treefile, soundfile, linemap = nil;
    ^super.newCopyArgs(treefile, soundfile, linemap).init;
  }

  *toSoundFile {
    arg treefile, soundfile, force=false;
    ^this.newCopyArgs(treefile,soundfile).init.toSoundFile(force);
  }
  
  *toBuffer {
    arg treefile, action = false;
    ^this.newCopyArgs(treefile).init.toBuffer(treefile,action);
  }

  *soundFileTo {
    arg treefile, soundfile, force=false;
    ^this.newCopyArgs(treefile, soundfile).init.fromSoundFile(force);
  }
  
  *bufferTo {
    arg treefile, buffer, action=false, force=false;
    ^this.newCopyArgs(treefile).init.fromBuffer(buffer, action, force);
  }

  init {

    if (treefile.class==PathName) {
      treefile = treefile.fullPath;
    }{
      treefile = treefile.asString;
    };

    tree = treeClass.new(treefile);

    this.treefile(treefile);
    
    if (linemap.isNil) {
      linemap = linemapClass.new(this.namingFromExtension(treefile));
    };

    tmp = tmpClass.new(16);

    server=defaultServer;
  }

  treefile {
    arg arg_treefile;
    treefile = arg_treefile;

    if (treefile.isNil) {
      SpaceTrackerError("treefile is required").throw;
    };
    tree.path = treefile;
    
  }

  namingFromExtension {
    arg filename;
    ^filename.splitext[1].asSymbol
  }

  soundFileName {
    arg channel;
    ^soundfile++if(channel > 0, $.++channel, "");
  }

  initSounds {
    sounds = this.soundFilesCollect({
      arg file;
      var sound;
      sound = soundfileClass.new
        .headerFormat_(headerFormat)
        .sampleFormat_(sampleFormat)
        .numChannels_(numChannels);
      File.delete(file);
      if (false == sound.openWrite(file)) {
        SpaceTrackerError("Could not open"+file+"for writing").throw;
      };
      sound.path = file; // Workaround for openWrite not assigning path
      sound
    });
  }

  soundFilesDo {
    arg action;
    polyphony.do({
      arg i;
      action.value(this.soundFileName(i));
    });
  }
  
  soundFilesCollect {
    arg action;
    var returns = Array.fill(polyphony);
    polyphony.do({
      arg i;
      returns.put(i, action.value(this.soundFileName(i)));
    });
    ^returns;
  }

  toCSV {
    this.soundFilesDo({
      arg file;
      SoundFile.new(file).toCSV(file++".csv");
    });
  }

  validateTreeRead {
    if (false == File.exists(tree.path)) {
      SpaceTrackerError(tree.path + "does not exist").throw;
    };
  }
  
  validateTreeWrite {
    if (File.exists(tree.path)) {
      SpaceTrackerError(tree.path + "exists, use 'force' to overwrite").throw;
    };
  }

  validateSoundRead {
    polyphony.do({
      arg i;
      var file;
      file = this.soundFileName(i);
      if (false == File.exists(file)) {
        SpaceTrackerError(file + "does not exist").throw;
      };
    });
  }
  
  validateSoundWrite {
    polyphony.do({
      arg i;
      var file;
      file = this.soundFileName(i);
      if (File.exists(file)) {
        SpaceTrackerError(file + "exists, use 'force' to overwrite").throw
      };
    });
  }

  openSounds {
    sounds = List.new;
    
    if (false == File.exists(soundfile)) {
      SpaceTrackerError(soundfile + "does not exist").throw;
    };
    block {
      var i, sound, file;
      i = 0;
      file = soundfile;
      while ({
        File.exists(file);
      }, {
        sound = soundfileClass.openRead(file);
        sounds.add(sound);
        //sources.add(i);
        i = i + 1;
        file = soundfile ++ $. ++ i;
      });
    };
  }

  writeSounds {
    arg force = false;
   
    this.validateTreeRead;
    if (false == force) {
      this.validateSoundWrite;
    };
    read = readClass.new(tree, linemap);
    numChannels = read.lineSize;
    polyphony = read.polyphony;
    this.initSounds;
    read.sounds = sounds;
    read.toNumeric;
  }

  writeTree {
    arg force = false;

    if (false == force) {
      this.validateTreeWrite;
    };
    File.delete(treefile);
    this.openSounds(soundfile);

    write = writeClass.new(sounds, tree, linemap);
    write.fromNumeric;
  }

  bufferTo {
    arg buffer, action, force;
    soundfile = tmp.file(soundExtension);
    if (polyphony > 1) {
      // TODO
    }{
      forkIfNeeded {
        buffer.write(soundfile, headerFormat, sampleFormat);
        server.sync;
        this.writeTree(force);
        action.value(this);
      };
    }
  }
  
  toSoundFile {
    arg force;
    if (soundfile.isNil) {
      soundfile = tmp.file(soundExtension);
    };
    this.writeSounds(force);
    ^if(polyphony==1,sounds[0],sounds);
  }

  toBuffer {
    arg action = false;
    this.toSoundFile(true);
    if (polyphony > 1, {
      var count = polyphony;
      ^sounds.collect({
        arg sound;
        Buffer.read(server, sound.path, 0, -1, {
          count = count - 1;
          if (count == 0) {
            action.value;
          };
          File.delete(sound.path);
        }).path_(treefile).numChannels_(1);
      });
    }, {
      ^Buffer.read(server, sounds[0].path, 0, -1, action).path_(treefile).numChannels_(1);
    });
  }
}

SpaceTrackerError : Error {
}

