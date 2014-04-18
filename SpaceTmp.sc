
SpaceTmp {
  var
    <>length
  ;
  *new {
    ^super.new(length);
  }

  rnd {
    ^length.collectAs({
      var rnd = 25.rand;
      (rnd + case({rnd < 10},48,87)).asAscii; // offsets for digits or lower case letters
    }, String);
  }
 
  file {
    arg extension;
    ^Platform.defaultTempDir +/+ this.class.name.asString.toLower ++ this.rnd ++ $. ++ extension;
  }
  
}

