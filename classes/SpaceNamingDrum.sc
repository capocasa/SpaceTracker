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


SpaceNamingDrum {
  var names, >length = 11;

  *new {
    ^super.new.init;
  }
  init {
    names = TwoWayIdentityDictionary[
      27 -> \laser,
      28 -> \whip,
      29 -> \push,
      30 -> \pull,
      31 -> \stick,
      32 -> \click,
      34 -> \blip,
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
      81 -> \trii,
      82 -> \shake,
    ];
  }

  string {
    arg note;
    ^names[note] ?? note;
  }

  number {
    arg note;
    ^names.getID(note);
  }
}

