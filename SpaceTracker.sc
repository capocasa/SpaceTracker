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
    <treefile,
    <>soundfile,
    <>linemap,
    <>tree,
    <>polyphony,
    <>numChannels,
    <>headerFormat="WAV",
    <>sampleFormat="float",
    <>soundExtension="wav",
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
    arg server, treefile, action = false;
    ^this.newCopyArgs(treefile).init.toBuffer(server, action);
  }

  *soundFileTo {
    arg treefile, soundfile, force=false;
    ^this.newCopyArgs(treefile, soundfile).init.soundFileTo(force);
  }
  
  *bufferTo {
    arg server, treefile, buffer, frames, action=false, force=false;
    ^this.newCopyArgs(treefile).init.bufferTo(server, buffer, frames, action, force);
  }

  init {

    if (treefile.class==PathName) {
      treefile = treefile.fullPath;
    }{
      treefile = treefile.asString;
    };

    tree = treeClass.new(treefile);

    this.treefile_(treefile);
    
    if (linemap.isNil) {
      linemap = linemapClass.new(this.namingFromExtension(treefile));
    };

    tmp = tmpClass.new(16);
  }

  treefile_ {
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
      File.delete(file); // Delete if already exists, will be recreated
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
    write.analyze.apply;
  }
  
  soundFileTo {
    arg force;
    this.writeTree(force);
  }

  bufferTo {
    arg server, buffer, frames, action, force;
    soundfile = tmp.file(soundExtension);
    polyphony = 0;
    forkIfNeeded {
      buffer.do {
        arg buffer, i;
        frames = frames.first;
        frames = frames.asInteger; // Workaround bug [#1827](https://github.com/supercollider/supercollider/issues/1827)
        buffer.write(this.soundFileName(i), headerFormat, sampleFormat, frames);
        polyphony = polyphony + 1;
      };
      server.sync;
      this.writeTree(force);
      action.value(this);
    };
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
    arg server, action = false;
    var buffer, count;
    if (sounds.isNil) {
      // If sounds already exist, create buffer from those
      this.toSoundFile(true);
    };
    count = polyphony;
    buffer = sounds.collect({
      arg sound, buffer;
      Buffer.read(server, sound.path, 0, -1, {
        count = count - 1;
        if (count == 0) {
          action.value;
        };
        File.delete(sound.path);
      }).path_(treefile).numChannels_(sound.numChannels);
    });
    if (server.serverRunning == false) {
      // No server? At least clean up
      sounds.do {
        arg sound;
        File.delete(sound.path);
      };
    };
    ^buffer;
  }
}

SpaceTrackerError : Error {
}

