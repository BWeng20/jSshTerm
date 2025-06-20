# jSshTerminal

Another SSH shell.

Works (currently) only for "**dumb**" and (partial) "**xterm**" terminal mode.

## Integration

See [SSHTerm Main](src/main/java/com/bw/sshTerm/SSHTerm.java) for 
an example how to integrate the terminal into an application.

The current implementation uses an JSch-shell-channel, but this can 
be changed easily as ssh-functionality is encapsulated into interfaces.
The terminal itself is not aware of the implementation.



