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
    <>map,
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
    <>maxnote = 7 // 7th power of 2 is 128, so shortest note specified by integer is 1/128th
  ;

  *new {
    arg treefile;
    ^super.newCopyArgs(treefile).init;
  }

  *initClass {

    map = IdentityDictionary.new;

    map[\drum] = TwoWayIdentityDictionary[
      35 -> \kicker,
      36 -> \kick,
      37 -> \rim,
      38 -> \snarer,
      39 -> \clap,
      40 -> \snare,
      41 -> \floor,
      42 -> \hat,
      43 -> \ceil,
      44 -> \pedal,
      45 -> \tom,
      46 -> \hatt,
      47 -> \tomm,
      48 -> \tommy,
      49 -> \crash,
      50 -> \tommyer,
      51 -> \ride,
      52 -> \china,
      53 -> \bell,
      54 -> \tam,
      55 -> \splash,
      56 -> \cow,
      57 -> \crash,
      58 -> \vibe,
      59 -> \rider,
      60 -> \bongo,
      61 -> \bongoo,
      62 -> \congga,
      63 -> \conga,
      64 -> \cong,
      65 -> \timbb,
      66 -> \timb,
      67 -> \aggo,
      68 -> \ago,
      69 -> \cab,
      70 -> \mar,
      71 -> \whis,
      72 -> \whiss,
      73 -> \guiro,
      74 -> \guiiro,
      75 -> \clav,
      76 -> \wood,
      77 -> \wod,
      78 -> \cuicc,
      79 -> \cuic,
      80 -> \tri,
      81 -> \trii
    ];

    map[\note] = {
      arg note, reverse;
      var mods,tones,octaves;
      mods = TwoWayIdentityDictionary[
        $b -> -1,
        $x ->  1,
        $c -> -2,
        $y ->  2,
      ];

      tones = TwoWayIdentityDictionary[
        $c -> 0,
        $d -> 1,
        $e -> 2,
        $f -> 3,
        $g -> 4,
        $a -> 5,
        $b -> 6
      ];
  
      octaves = TwoWayIdentityDictionary[
        $0 -> 2,
        $1 -> 3,
        $2 -> 4,
        $3 -> 5,
        $4 -> 6,
        $5 -> 7,
        $6 -> 8,
        $7 -> 9,
        $8 -> 10,
        $9 -> 11
      ];
    

      if(reverse,{
        var octave, tone, mod, semi;
        semi = Scale.major.semitones;
        tone = (note % 12).asFloat;
        octave = ((note - tone)/12).asInteger;
        mod = if(semi.indexOf(tone).isNil,1,0);
        tone = (tone-mod).asFloat;
        tone = semi.indexOf(tone);
        tone= map[\note].getID(tone);
        octave=map[\octave].getID(octave);
        mod=map[\mod].getID(mod)?"";
        ^tone++octave++mod;
      },{
        var string;
        string = note.asString.toLower;
        if ("^[a-g][0-9]?[bxcy]?$".matchRegexp(string), {
          var octave, tone, mod;
          tone = tones.at(note[0]);
          octave = octaves.at(note[1]);
          mod = mods.at(note[2]) ? 0;
          ^ 12 * octave + tone + mod;
        },{
          "Could not understand the notation for the note value".throw;
        });
      });
    };
  
    lengths = IdentityDictionary.new;

    lengths[\drum] = 11;
    lengths[\note] = 3;

    treeClass = SpaceTree;
    soundClass = SoundFile;
  }

  init {
    server=Server.default;
  }

  *fromSoundFile {
    arg treefile, soundfile, naming=\note;
    ^this.new(treefile).fromSoundFile(soundfile,naming);
  }

  fromSoundFile {
    arg soundfile, naming=\note;
    var sound, tracker, tree, line, samples;
    sound = soundClass.new;
    sound.openRead(soundfile);
    
    if(File.exists(treefile)) { (treefile + "exists").throw };
    
    polyphony = sound.numChannels;
    
    tree = SpaceTree.new(treefile);
    
    samples = FloatArray.newClear(polyphony);
    
    while ({
      sound.readData(samples);
      samples.size > 0;
    }, {
      line = this.formatNote(samples, naming);
      tree.write(line, [3,lengths.at(naming)]);
    });

    sound.close;
  }

  *fromBuffer {
    arg treefile, buffer, action;
    ^this.class.new(treefile).fromBuffer;
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
    var space, sound, tmp, chunk, counter;

    if (soundfile.isNil) {
      soundfile = this.tmpFileName;
    };

    space = treeClass.new(treefile);

    sound = soundClass.new
      .headerFormat_(headerFormat)
      .sampleFormat_(sampleFormat)
      .numChannels_(polyphony);
    sound.openWrite(soundfile);
    
    chunk = FloatArray.new(chunksize - (chunksize % polyphony));
    
    counter = 0;

    space.parse({
      arg line, indent, lastindent;
      if (line.notNil) {
        line = this.unformat(line);
        sound.writeData(line)
      };
    });
  
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
    arg samples, naming;
    var time, note, line;
  
    line = Array.newFrom(samples);

    time = line[0];
    note = line[1];

    time = this.formatTime(time);
    note = this.formatNote(note, naming);
  
    line[0] = time;
    line[1] = note;
    ^line;
  }
  
  unformat {
    arg line, naming;

    var
      time,
      note
    ;

    time = line[0];
    time = this.unformatTime(time);

    note = line[1];
    note = this.unformatNote(note, naming);

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
    arg note, naming;
    var mapper;
    mapper = map[naming];

    ^switch(mapper.class,
    TwoWayIdentityDictionary, {
      mapper.at(note.asInteger);
    },
    Function, {
      mapper.value(note: note, reverse: false);
    });
  }

  unformatNote {
    arg note, naming;
    var mapper;

    mapper = map[naming];

    ^switch(mapper.class,
    TwoWayIdentityDictionary, {
      mapper.getID(note);
    },
    Function, {
      mapper.value(note: note, reverse: true);
    });
  }

  tmpName {
    arg length = 12;
    ^"abcdefghijklmnopqrstuvwxyz".scramble.copyRange(0,12);
  }

  tmpFileName {
    ^Platform.defaultTempDir +/+ this.tmpName ++ $. ++ headerFormat.toLower;
  }
}

