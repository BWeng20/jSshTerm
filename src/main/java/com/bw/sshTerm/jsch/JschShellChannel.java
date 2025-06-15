package com.bw.sshTerm.jsch;

import com.bw.sshTerm.ShellChannel;
import com.bw.sshTerm.TerminalControl;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Connections to a Jsch ssh shell.
 */
public class JschShellChannel implements ShellChannel {

    private ChannelShell channel;
    private OutputStream inputToShell;
    private InputStream shellOutput;
    private TerminalControl ctrl;
    private JSch jsch;


    @Override
    public void write(byte[] data) {
        if (channel != null) {
            try {
                inputToShell.write(data);
                inputToShell.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    /**
     * Starts a session. <br>*
     *
     * @throws IOException Throws if JSch fails due to some io-operation.
     */
    public void connect(String user, String password, String host, int port, TerminalControl terminalControl) throws IOException {

        try {
            jsch = new JSch();
            Session session = jsch.getSession(user, host, port);

            UserInfo userInfo = new UserInfo(terminalControl.getPane());
            userInfo.setUserName(user);
            userInfo.setPassword(password);

            session.setPassword(password);
            session.setUserInfo(userInfo);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            this.channel = (ChannelShell) session.openChannel("shell");
            this.ctrl = terminalControl;
            channel.setPty(true);
            channel.setPtyType(terminalControl.getPtyType());

            shellOutput = channel.getInputStream();
            inputToShell = channel.getOutputStream();
            channel.connect();

            Thread rt = new Thread(this::readShellOutput);
            rt.setDaemon(true);
            rt.start();
        } catch (IOException e) {
            throw e;
        } catch (JSchException je) {
            throw new IOException("Can't connect", je);
        }
    }

    /**
     * Reads output from shell channel and calls terminal-control to handle it.
     */
    protected void readShellOutput() {
        try {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = shellOutput.read(buffer)) != -1) {
                byte[] answer = ctrl.handleShellOutput(buffer, bytesRead);
                if (answer != null)
                    write(answer);
            }
            System.out.println("Connection terminated");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Needs to be called if shell shall be closed.
     */
    public void disconnect() {
        if (this.channel != null) {
            this.channel.disconnect();
            this.channel = null;
        }
    }

    @Override
    public void setPtySize(int termWidth, int termHeight, int charWidth, int charHeight) {
        if (channel != null)
            channel.setPtySize(termWidth, termHeight, charWidth, charHeight);
    }
}
