
// Writes SpaceTracker files from their rendered soundfile form

SpaceWrite {

  fromSoundFile {
    arg soundfile;
    var tree, numChannels, changes;
     
    tree = SpaceTree.new(treefile);
    
    // The first pass will fill this with instructions
    // for the second pass
    changes = Array.new;

    // First pass: Discover overlaps in sound files

    block {
      // Variables that persist through each iteration
      var
        previousOverlap,
        latestEnd,
        previousEnd,
        previousType
      ;
        
      // Initialize
      previousOverlap = false;
      latestEnd = 0;
      previousEnd = 0;
      previousType = nil;
      
      this.soundFilesDo(soundfile, {
        arg lines,begins,ends,notes,times,drop;
        // Variables that get re-assigned for every iteration
        var
          isNote,
          index,
          overlapBackward,
          overlapForward,
          overlap,
          parallel,
          type
        ;

        // Default values
        overlap = false;
        overlapBackward = false;
        overlapForward = false;

        // Let's get started!

        // Loop until all lines from all sound files have been consumed   
        if (drop.isNil, {
          index = begins.minIndex;
        },{
          index = drop;
        });
        
        if (drop.isNil, {
          // detect overlap
          overlapBackward = previousEnd > begins[index];
          
          if (begins.size > 1, {
            var index2 = begins.order[1];
            if (ends[index] > latestEnd) {
              latestEnd = ends[index];
            };
            overlapForward = latestEnd > begins[index2];
          },{
            overlapForward = false;
          });

          overlap = overlapBackward || overlapForward;
          
          // detect section change
          parallel = nil;
          if (overlap && (false == previousOverlap)) {
            parallel = true;
          };
          
          if ((false == overlap) && previousOverlap) {
            parallel = false;
          };
          
          previousEnd = ends[index];
        });
        
        // Debug
        
        // Keep this debug output around, it's the bread
        // and butter of developing this algorithm more easily
        
        [
          switch(parallel, false, "<", nil, " ", true, ">"),
          if(overlap, "8", "o"),
          //if(previousOverlap, \previousOverlap, \nopreviousOverlap),
          if(overlapBackward, ":", "."),
          if(overlapForward, "=", "-"),
          begin: begins[index],
          end: ends[index],
          note: this.convertToSymbolicNote(notes[index]),
          index: index
          //time: times[index]
        ].postln;
       
        // Save guidance to inform the second pass
        //changes=changes.add(type);
        //changes=changes.add(0);
        //changes.atInc(changes.size-1);
        if (parallel.notNil, {
          changes.add(parallel);
          changes.add(begins[index]);
        });

        // Lookbehind
        previousOverlap = overlap;

        // Return value marks consumed
        index;
      });
    };

    changes.postln;

    // Second pass: Write to tree using information collected in first pass
  
    block {
      // Variables that persist through each iteration
      var
        index,
        parallel,
        paralleled,
        changed,
        begin
      ;

      // Initialization
      index = 0;
      parallel = false;
      begin = 0;
      changed = 0;
      paralleled = 0;

      this.soundFilesDo(soundfile, {
        arg lines,begins,ends,notes,times,drop;
      
        // Variables that get re-assigned for every iteration
        var
          line,
          indent,
          parallel
        ;

        parallel = false;
        
        if (drop.notNil, {
          index = drop;
          indent = 0;
        },{
          
          if (ends.at(index) >= changed, {
            if (parallel, {
              index = begins.minIndex;
              if (paralleled == lines.size, {
                #parallel, changed = changes.removeAt(0);
                paralleled = 0;
              }, {
                paralleled = paralleled + 1;
              });
              indent = 1;
            },{
              #parallel, changed = changes.removeAt(0);
            });
          },{
            if (parallel, {
              indent = 2;
            });
          });

          if (false == parallel, {
            index = begins.minIndex;
            indent = 0;
          });
          
          line = lines[index];
          line = this.convertToSymbolic(line);
          
          tree.write(line, indent);
          
        });

        index;
      });
    }
  }

  soundFilesDo {
    arg soundfile,callback;
    var
      sounds,
      lines,
      begins,
      ends,
      delta,
      polyphony,
      numChannels,
      consumed,
      notes,
      times,
      isNote,
      drop
    ;
    
    sounds = this.openSoundFiles(soundfile);
    polyphony = sounds.size;
    numChannels = sounds[0].numChannels;
    lines = Array.newClear(polyphony);
    begins = Array.fill(polyphony, 0);
    ends = Array.fill(polyphony, 0);
    delta = Array.fill(polyphony, 0);
      
    block {
      arg break;
      while ({true}, {
        // Fill up a buffer of one line per polyphonic channel
        // (used to locate note ends and null notes)

        // as opposed to lines.size.do or lines.reverseDo, this
        // allows removeAt with correct indices
        lines.size.reverseDo({
          arg i;
          var line;
          line = lines[i];

          // Make sure this element of the lines buffer is full, not nil
          // decrease buffer size if sound has exhausted

          // Line can be nil, when:
          // - just initialized
          // - consumed and made note of pause

          // - consumed and written to tree file
          if ( line.isNil ) {
            line = FloatArray.newClear(numChannels);
            sounds[i].readData(line);
            
            if (line.size == numChannels, {
              lines.put(i, line);
              
              delta.put(i, line.at(0));
              
              ends.atInc(i, delta.at(i));
              
            },{
              sounds.removeAt(i);
              lines.removeAt(i);
              begins.removeAt(i);
              ends.removeAt(i);
              delta.removeAt(i);
            });
          };
        });
        
        // Termination when all soundfiles depleted
        if (lines.size == 0) {
          break.();
        };
        
        notes = lines.collect({arg line; line[1]});
        times = lines.collect({arg line; line[0]});
        isNote = notes.collect({arg note, i; note != 0 });
        
        drop = isNote.indexOf(false);

        consumed = callback.(lines,begins,ends,notes,times,drop);

        if (consumed.isNil) {
          "Please return the index to consume".throw;
        };

        // Beginning and end of consumed note are not the same.
        // End of consumed note will increase again when received new
        // line from soundfile
        begins.atInc(consumed, lines.at(consumed).at(0));

        lines.put(consumed, nil);

      });
    }
  }
  
  openSoundFiles {
    arg soundfile;
    var sounds;
    sounds = List.new;
    
    if (false == File.exists(soundfile)) {
      (soundfile + "does not exist").throw;
    };
    block {
      var i, sound, file;
      i = 0;
      file = soundfile;
      while ({
        File.exists(file);
      }, {
        sound = soundClass.openRead(file);
        sounds.add(sound);
        //sources.add(i);
        i = i + 1;
        file = soundfile ++ $. ++ i;
      });
    };
    ^sounds;
  }


}
