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
    tmpClass,
    soundfileClass,
    defaultServer
  ;

  var
    <>tree,
    <>linemap,
    <>polyphony,
    <>server,
    <>headerFormat="AIFF",
    <>sampleFormat="float",
    <>soundExtension="aif",
    <>sounds,
    <>tmp
  ;

  *initClass {
    readClass = SpaceRead;
    linemapClass = SpaceLinemap;
    tmpClass = SpaceTmp;
    soundfileClass = SoundFile;
    defaultServer = Server.default;
  }

  *new {
    arg treefile, polyphony = 8;
    ^super.newCopyArgs.init(treefile, polyphony);
  }

  init {
    arg treefile;
    var ext, tmp;
    
    if (treefile.isNil) {
      ("treefile is required").throw;
    };

    tree = SpaceTree.new(treefile);

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

  toSoundFile {
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
      sound = soundfileClass.new
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

    ^readClass.toSoundFile;
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

