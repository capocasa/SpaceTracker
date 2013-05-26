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
    <>treeClass = SpaceTree,
    <>soundClass = SoundFile,
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

  init {
    arg arg_filename, arg_polyphony;
    filename = arg_filename;
    polyphony = arg_polyphony;
  }

  asSoundFile {
    var space, sound, tmp, chunk, counter;

    space = treeClass.new;

    sound = SoundClass.new
      .headerFormat_(headerFormat)
      .sampleFormat_(sampleFormat)
      .numChannels_(polyphony);
    tmp = this.tmpFileName;
    file.openWrite(tmp);
      
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

  tmpFileName {
    ^Platform.defaultTempDir +/+ filename +/+ headerFormat.lowercase;
  }

  

}

