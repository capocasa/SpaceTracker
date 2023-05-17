/*
    SpaceTracker for SuperCollider 
    Copyright (c) 2013 - 2017 Carlo Capocasa. All rights reserved.
    https://capocasa.net

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
*/

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
    <>tmpFile = false,
    <>read,
    <>write,
    <>frames
  ;

  *new {
    arg treefile, soundfile, linemap = nil;
    ^super.newCopyArgs(soundfile, linemap).init(treefile);
  }

  *toSoundFile {
    arg treefile, soundfile;
    ^super.newCopyArgs(soundfile).init(treefile).toSoundFile;
  }
  
  *toBuffer {
    arg server, treefile;
    ^super.new.init(treefile).toBuffer(server);
  }

  *fromSoundFile {
    arg treefile, soundfile;
    ^super.newCopyArgs(soundfile).init(treefile).fromSoundFile;
  }
  
  *fromBuffer {
    arg treefile, buffer, frames = nil;
    ^super.new.init(treefile).fromBuffer(buffer, frames);
  }

  init {
    arg treefile;
    tree = SpaceTree(treefile);
    tmp = SpaceTmp(16);
    if (linemap.isNil) {
      linemap = SpaceLinemap.newFromExtension(tree.path);
      if (linemap.isNil) {
        linemap=SpaceLinemap.newFromTree(tree);
      };
      if (linemap.isNil) {
        SpaceNamingError("Could not detect naming from file extension or automatically").throw;
      }
    }
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

  readSoundFilesCollect{
    arg action;
    var list = List[];
    this.readSoundFilesDo { |file, i|
      list.add(action.value(file, i));
    };
    ^list.asArray;
  }

  readSoundFilesDo {
    arg action;
    var i, file;
    if (false == File.exists(soundfile)) {
      SpaceTrackerError(soundfile + "does not exist").throw;
    };
    i = 0;
    file = soundfile;
    while ({
      File.exists(file);
    }, {
      action.value(file, i-1);
      i = i + 1;
      file = soundfile ++ $. ++ i;
    });
  }

  openSounds {
    sounds = List.new;
    this.readSoundFilesDo { |file, i|
      var sound;
      sound = SoundFile.openRead(file);
      sounds.add(sound);
    };
    ^sounds.asArray;
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
    File.delete(tree.path);
    this.openSounds(soundfile);

    write = SpaceWrite(sounds, tree, linemap);
    write.analyze.apply;
  
    if (tmpFile) {this.soundFilesDo {|f|File.delete(f)}};
  }
  
  fromSoundFile {
    this.writeTree;
  }

  fromBufferInit {
    arg buffer;
    if (soundfile.isNil) {
      soundfile = tmp.file(soundExtension);
      tmpFile = true;
    };
    polyphony = buffer.size;
    buffer.do {
      arg buffer, i;
      var path;
      path=this.soundFileName(i);
      buffer.writeTimed(path, headerFormat);
    };
  }

  fromBuffer {
    arg buffer, argFrames = nil;
    frames = argFrames;
    buffer=buffer.asArray;
    forkIfNeeded {
      this.fromBufferInit(buffer);
      if (frames.asArray.every({|e|e==1})) {
        "No frames, not saving %".format(tree.path).warn;
        this.yield;
      };
      buffer[0].server.sync;
      this.writeTree;
    };
  }
  
  toSoundFile {
    if (soundfile.isNil) {
      soundfile = tmp.file(soundExtension);
      tmpFile = true;
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
        if (tmpFile) {File.delete(sound.path);};
      }).path_(tree.path).numChannels_(sound.numChannels);
    });
    if (server.serverRunning == false) {
      // No server? At least clean up
      sounds.do {
        arg sound;
        if (tmpFile) {File.delete(sound.path);};
      };
    };
    ^if(buffer.size==1,buffer.first,buffer);
  }

}

SpaceTrackerError : Error {
}

