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

  *new {
    arg treefile, soundfile, linemap = nil;
    ^super.newCopyArgs(treefile, soundfile, linemap).init;
  }

  *toSoundFile {
    arg treefile, soundfile;
    ^this.newCopyArgs(treefile,soundfile).init.toSoundFile;
  }
  
  *toBuffer {
    arg server, treefile;
    ^this.newCopyArgs(treefile).init.toBuffer(server);
  }

  *soundFileTo {
    arg treefile, soundfile;
    ^this.newCopyArgs(treefile, soundfile).init.soundFileTo;
  }
  
  *bufferTo {
    arg treefile, buffer, frames=nil;
    ^this.newCopyArgs(treefile).init.bufferTo(buffer, frames);
  }

  init {
    tree = SpaceTree(treefile);
    this.treefile_(treefile);
    tmp = SpaceTmp(16);
    if (linemap.isNil) {
      linemap = SpaceLinemap.new(this.namingFromExtension(treefile));
    };
    if (treefile.class==PathName) {
      treefile = treefile.fullPath;
    }{
      treefile = treefile.asString;
    };
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
      sound = SoundFile.new
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
        sound = SoundFile.openRead(file);
        sounds.add(sound);
        //sources.add(i);
        i = i + 1;
        file = soundfile ++ $. ++ i;
      });
    };
  }

  writeSounds {
    this.validateTreeRead;
    read = SpaceRead(tree, linemap);
    numChannels = read.lineSize;
    polyphony = read.polyphony;
    this.initSounds;
    read.sounds = sounds;
    read.toNumeric;
  }

  writeTree {
    File.delete(treefile);
    this.openSounds(soundfile);

    write = SpaceWrite(sounds, tree, linemap);
    write.analyze.apply;
  }
  
  soundFileTo {
    this.writeTree;
  }

  // RecordBufS can never record a zero pause, because
  // a trigger will always be at least one control period.
  // DetectEndS finds the first zero pause, which marks
  // then end of a recording.
  autoframes {
    arg buffer;
    var path, responder, id, frames;
    id = 262144.rand;
    path = '/finalFrameS';
    responder = OSCFunc({|msg|
      if (msg[2] == id) {
        frames = msg[3..];
      };
    }, path);
    {
      SendReply.kr(Impulse.kr, path, DetectEndS.kr(buffer), id);
      FreeSelf.kr(Impulse.kr);
    }.play(buffer[0].server.defaultGroup);
    buffer[0].server.sync;
    responder.free;
    ^frames;
  }

  bufferToInit {
    arg buffer, frames = nil;
    soundfile = tmp.file(soundExtension);
    polyphony = buffer.size;
    if (frames.isNil) {
      frames = this.autoframes(buffer);
    };
    buffer.do {
      arg buffer, i;
      var path, framesi;
      framesi = if(frames.isArray, frames[i], frames);
      path=this.soundFileName(i);
      buffer.write(path, headerFormat, sampleFormat, framesi);
    };
  }

  bufferTo {
    arg buffer, frames = nil;
    forkIfNeeded {
      this.bufferToInit(buffer, frames);
      buffer[0].server.sync;
      this.writeTree;
    };
  }
  
  toSoundFile {
    if (soundfile.isNil) {
      soundfile = tmp.file(soundExtension);
    };
    this.writeSounds;
    ^sounds;
  }

  toBuffer {
    arg server;
    var buffer;
    if (sounds.isNil) {
      // If sounds already exist, create buffer from those
      this.toSoundFile;
    };
    buffer = sounds.collect({
      arg sound, buffer;
      Buffer.read(server, sound.path, 0, -1, {
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

  *alloc {
    arg server, polyphony=1, numChannels=1, frames = 16384;
    ^polyphony.collect{Buffer.alloc(server, frames, numChannels + 1)};
  }

}

SpaceTrackerError : Error {
}

