
SpaceTmp {
  var
    extension,
    length
  ;

  *new {
    arg extension="wav", length = 12;
    ^super.newCopyArgs(extension, length);
  }

  rnd {
    ^length.collectAs({
      var rnd = 25.rand;
      (rnd + case({rnd < 10},48,87)).asAscii; // offsets for digits or lower case letters
    }, String);
  }
 
  file {
    ^Platform.defaultTempDir +/+ this.class.name.asString.toLower ++ this.rnd ++ $. ++ extension;
  }
  
}

