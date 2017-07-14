
SpaceNamingNum {
  var names, >length = 11;

  *new {
    ^super.new.init;
  }
  init {
  }

  string {
    arg note;
    ^note.asString;
  }

  number {
    arg note;
    ^note.asFloat;
  }
}

