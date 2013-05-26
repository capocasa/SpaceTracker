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
    chunksize = 1024
  ;

  var
    <>filename,
    <>headerFormat="AIFF",
    <>sampleFormat="float",
    <>polyphony = 4
  ;

  *new {
    arg filename, polyphony;
    ^super.new.init(filename, polyphony);
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
  }

  init {
    arg arg_filename, arg_polyphony;
    filename = arg_filename;
    polyphony = arg_polyphony;
  }

  asSoundFile {
    arg treeClass = SpaceTree, soundClass = SoundFile;
    var space, sound, tmp, chunk, counter;

    space = treeClass.new;

    sound = soundClass.new
      .headerFormat_(headerFormat)
      .sampleFormat_(sampleFormat)
      .numChannels_(polyphony);
    tmp = this.tmpFileName;
    sound.openWrite(tmp);
      
    chunk = FloatArray.new(chunksize * polyphony);
    
    counter = 0;

    space.parse({
      arg line, indent, lastindent;
      var i;
      
      i = 0;
      while (i < polyphony) {
        line = this.numerize(line);

        i = i + 1;
        counter = counter + 1;
      }
      
    });
  }

  numerize {
    arg line;

    var
      time,
      note,
      samples,
      size,
      i
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

  tmpFileName {
    ^Platform.defaultTempDir +/+ filename +/+ headerFormat.lowercase;
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

}

