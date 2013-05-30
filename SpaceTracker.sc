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
    <>mods,
    <>notes,
    <>octaves,
    <>treeClass,
    <>soundClass,
    chunksize = 1024
  ;

  var
    <>filename,
    <>server,
    <>headerFormat="AIFF",
    <>sampleFormat="float",
    <>polyphony = 4,
    <>maxnote = 7 // 7th power of 2 is 128, so shortest note specified by integer is 1/128th
  ;

  *new {
    arg filename;
    ^super.newCopyArgs(filename).init;
  }

  *initClass {

    map = TwoWayIdentityDictionary[
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

    mods = TwoWayIdentityDictionary[
      $b -> -1,
      $x ->  1,
      $c -> -2,
      $y ->  2,
    ];

    notes = TwoWayIdentityDictionary[
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
  
    treeClass = SpaceTree;
    soundClass = SoundFile;
  }

  init {
    server=Server.default;
  }

  *fromSoundFile {
    arg treefile, soundfile;
    var sound, tree;
    sound = soundClass.new.openRead(soundfile);
    polyphony = sound.numChannels;

    if(PathName.new(filename).exists) { filename + "exists".throw };
    tree = File.open(filename, "a");
    
    sound.close;
  }

  *fromBuffer {
    arg treefile, buffer, action;
    var soundfile, tracker;
    soundfile = this.tmpFileName;
    buffer.write(soundfile, headerFormat, sampleFormat, -1, 0, false, {
      tracker = this.class.fromSoundFile(soundfile);
      action.value(tracker);
    });
  }

  toSoundFile {
    arg soundfile;
    var space, sound, tmp, chunk, counter;

    if (soundfile.isNil) {
      soundfile = this.tmpFileName;
    };

    space = treeClass.new(filename);

    sound = soundClass.new
      .headerFormat_(headerFormat)
      .sampleFormat_(sampleFormat)
      .numChannels_(polyphony);
    sound.openWrite(soundfile);
    
    chunk = FloatArray.new(chunksize * polyphony);
    
    counter = 0;

    space.parse({
      arg line, indent, lastindent;
      if (line.notNil) {
        line = this.numerize(line);
        sound.writeData(line)
      };
    });
  
    sound.close;
    ^soundfile;
  }

  toBuffer {
    var soundfile, action;
    soundfile = this.asSoundFile;
    ^Buffer.read(server, soundfile, 0, -1, action);
  }

  /* These are not part of the public interace and might change */

  timify {
    arg time;
    var attempt;
    attempt = 1;
    block {
      arg break;
      for (1, maxnote, {
        attempt = attempt * 2;
        if (attempt.asInteger==attempt) {
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

  namify {
    arg note, naming;
    switch(
      naming,
      \drum, {
        note = map.getID(\drum) ? note;
      },
      \note, {
        note = this.str(note);
      },{
        // do nothing
      }
    );
    ^note;
  }

  notify {
    arg samples, naming = \note;
    var time, note;
  
    time = samples[0];
    note = samples[1];

    time = this.timify(time);
    note = this.namify(note);
  
    samples[0] = time;
    samples[1] = note;
    ^samples;
  }

  numerize {
    arg line;

    var
      time,
      note
    ;

    time = line[0];
    time = case
      { time.class == Integer } { 1/time  }
      { time }
    ;

    note = line[1];
    note = case
      { note.class==Symbol } { this.map(note) }
      { note }
    ;

    line[0] = time;
    line[1] = note;

    ^FloatArray.newFrom(line);
  }

  tmpName {
    arg length = 12;
    ^"abcdefghijklmnopqrstuvwxyz".scramble.copyRange(0,12);
  }

  tmpFileName {
    ^Platform.defaultTempDir +/+ this.tmpName ++ $. ++ headerFormat.toLower;
  }

  map {
    arg symbol;
    var string,result;
    
    // Try for drum map
    result = map.getID(symbol);
    if (result.notNil) {^result};
 
    string = symbol.asString.toLower;

    if ("^[a-g][0-9]?[bxcy]?$".matchRegexp(string)) {^this.int(string)};

    "Could not understand the notation for the note value".throw;
  }

  int {
    arg note;
    var octave, tone, modd;
    tone = notes.at(note[0]);
    octave = octaves.at(note[1]);
    modd = mods.at(note[2]) ? 0;
    ^ 12 * octave + tone + modd;
  }

  str {
    arg note;
    var octave, tone, modd;
    ^ notes.getID(tone)++octaves.getID(octave)++(mods.getID(modd)?"");
  }

}

