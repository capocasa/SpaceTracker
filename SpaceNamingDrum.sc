
SpaceNamingDrum {
  var names, >length = 11;

  *new {
    ^super.new.init;
  }
  init {
    names = TwoWayIdentityDictionary[
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
  }

  string {
    arg note;
    ^names[note];
  }

  number {
    arg note;
    ^names.getID(note);
  }
}

