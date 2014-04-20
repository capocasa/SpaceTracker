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
    <>tree,
    <>linemap,
    <>polyphony = 8,
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
    arg treefile, soundfile;
    ^super.newCopyArgs(treefile, soundfile).init;
  }

  *toSoundFile {
    arg treefile, soundfile, force=false;
    ^this.newCopyArgs(treefile,soundfile).init.toSoundFile(force);
  }
  
  *toBuffer {
    arg treefile, action = false;
    ^this.newCopyArgs(treefile).init.toBuffer(treefile,action);
  }

  *fromSoundFile {
    arg treefile, soundfile, force=false;
    ^this.newCopyArgs(treefile, soundfile).init.fromSoundFile(force);
  }
  
  *fromBuffer {
    arg treefile, buffer, action;
    ^this.newCopyArgs(treefile).init.fromBuffer(buffer);
  }

  init {

    tree = treeClass.new(treefile);

    this.treefile(treefile);


    tmp = tmpClass.new(16);

    server=defaultServer;
  }

  treefile {
    arg arg_treefile;
    treefile = arg_treefile;
    if (treefile.isNil) {
      ("treefile is required").throw;
    };
    tree.path = treefile;
    
    linemap = linemapClass.new(this.namingFromExtension(treefile));
  }

  namingFromExtension {
    arg filename;
    ^filename.splitext[1].asSymbol
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
    sounds = this.soundFilesCollect({
      arg file;
      var sound;
      sound = soundfileClass.new
        .headerFormat_(headerFormat)
        .sampleFormat_(sampleFormat)
        .numChannels_(numChannels);
      File.delete(file);
      if (false == sound.openWrite(file)) {
        ("Could not open"+file+"for writing").throw;
      };
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
      (tree.path + "does not exist").throw;
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
  
  openSounds {
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

    this.initChannels;
    this.initSounds;

    read = readClass.new(tree, sounds, linemap);
    read.toNumeric;
  }

  writeTree {
    arg force = false;
 
    this.openSounds(soundfile);

    write = writeClass.new(sounds, tree, linemap);
    write.fromNumeric;
  }

  loadBuffer {
    arg buffer, action;
    soundfile = tmp.file(soundExtension);
    buffer.write(soundfile, headerFormat, sampleFormat, -1, 0, false, {
      this.writeSounds(soundfile);
      action.value(this);
    });
  }
  
  saveBuffer {
    arg action = false;
    this.toSoundFile(true);
    if (Array == soundfile.class, {  
      ^soundfile.collect({
        arg file;
        Buffer.read(server, file, 0, -1, action);
      });
    }, {
      ^Buffer.read(server, soundfile, 0, -1, action);
    });
  }

}

