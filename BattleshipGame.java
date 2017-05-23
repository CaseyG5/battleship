package battleshipgame;

import java.awt.*;
import java.awt.EventQueue;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import java.net.*;
import java.io.*;

class GameFrame extends JFrame {
    public static final int TOTAL_WIDTH = 1060;
    public static final int TOTAL_HEIGHT = 760;
    
    public static final boolean MINE = true;
    public static final boolean THEIRS = false;
    
    JMenuBar menuBar;
    JMenu gameMenu;
    JMenu putShips;
    JMenu rollDice;
    
    JPanel gamePanel;
    DGrid defendPanel;                          // grids for 
    AGrid attackPanel;                          // player 1 & 2
    
    JPanel lowerPanel;
    JPanel connPanel;
    JButton connectButton;
    JLabel status;
    
    ConnectDialog cd;                           // dialog box to serve/join game
    
    JPanel messagePanel;
    JPanel sndMsgPanel, rcvMsgPanel;
    JPanel sendPanel;
    JLabel sent, received;
    JScrollPane outPane, inPane;
    JTextArea textOut, textIn;                  // read only areas
    JTextField toSend;
    JButton sendButton;
    Color gray, ocean;
    Border green, red;
    
    private int xCoord;                         // coordinates
    private int yCoord;                         // of shot fired
    
    private Ship[] fleet;
    
    private int port = 1776;
    private String addr;
    private InetAddress host;
    
    private Receiver msgReceiver;
    private ObjectOutputStream outstr;
    
    private boolean done = false;
    
    private short myRoll, theirRoll;            // to decide who's first
    private boolean whosTurn = THEIRS;
    private boolean shotResult;
    private short hitCount;
    
    public GameFrame() {
        setTitle("Battleships");
        setSize(TOTAL_WIDTH, TOTAL_HEIGHT);
        setLayout(new BorderLayout());
        
        gray = new Color(228,228,228);
        ocean = new Color(90,180,250);
        green = BorderFactory.createLineBorder(Color.GREEN, 3);
        red = BorderFactory.createLineBorder(Color.RED, 3);
        
        fleet = new Ship[11];
        
        // Menus
        menuBar = new JMenuBar();
        setJMenuBar(menuBar);
        
        gameMenu = new JMenu("Game");
        
        putShips = new JMenu("Place Ships");

            putShips.add( new AbstractAction("All Randomly") {
                public void actionPerformed(ActionEvent evt) {
                    
                    if(fleet[0] == null) {              // if ships not yet added
                        addAllShips();
                        textIn.append("   11 ships placed - ready\n");
                        connectButton.setEnabled(true);
                    }
                    else textIn.append("   Ships already placed\n");
                }
            });
            putShips.addSeparator();
            putShips.add( new AbstractAction("Clear All") {
                public void actionPerformed(ActionEvent evt) {
                    clearShips();
                    textIn.append("   Ships cleared - not ready\n");
                    connectButton.setEnabled(false);
                    
                }
            });
            
        rollDice = new JMenu("Roll");
        rollDice.setEnabled(false);
        
            rollDice.add(new AbstractAction("20-sided die") {
                public void actionPerformed(ActionEvent evt) {
                    myRoll = rollD20();
                }
            });
            
        gameMenu.add(putShips);
        gameMenu.add(rollDice);
            
        menuBar.add(gameMenu);
        
        // NORTH panel to keep track of game state
        gamePanel = new JPanel();
        gamePanel.setLayout(new GridLayout(1,2));
        
        defendPanel = new DGrid(ocean);
        attackPanel = new AGrid(Color.DARK_GRAY);
        
        gamePanel.add(defendPanel);
        gamePanel.add(attackPanel);
        
        add(gamePanel, BorderLayout.CENTER);
        
        lowerPanel = new JPanel();
        lowerPanel.setLayout(new BorderLayout());
        
        add(lowerPanel, BorderLayout.SOUTH);
        
        // CENTER panel to connect/disconnect
        connPanel = new JPanel();
        connPanel.setBorder(red);
        
        connectButton = new JButton("Connect...");
        connectButton.setEnabled(false);
        status = new JLabel("not connected");
        
        connPanel.add(connectButton);
        connPanel.add(status);
        lowerPanel.add(connPanel, BorderLayout.NORTH);
        
        // SOUTH panel for messaging
        messagePanel = new JPanel();
        messagePanel.setBorder(BorderFactory.createLineBorder(gray, 7));
        messagePanel.setLayout(new GridLayout(1,2));
        
        sndMsgPanel = new JPanel();
        rcvMsgPanel = new JPanel();
        sendPanel = new JPanel();
        
        sent = new JLabel("Sent messages:");
        received = new JLabel("Received messages:");
        textOut = new JTextArea(5,30);
          textOut.setEditable(false);
        
        outPane = new JScrollPane(textOut);
        outPane.setBorder(BorderFactory.createLineBorder(gray, 5));
        
                    // how to fix scrollpane behavior?
                    
        textIn = new JTextArea(6,30);
          textIn.setEditable(false);
          
        inPane = new JScrollPane(textIn);
        inPane.setBorder(BorderFactory.createLineBorder(gray, 5));
          
        toSend = new JTextField(35);
        
        sendButton = new JButton("Send");
        sendButton.setEnabled(false);
        
        sendPanel.setLayout(new FlowLayout());
        sendPanel.add(toSend);
        sendPanel.add(sendButton);
        
        sndMsgPanel.setLayout(new BorderLayout());
        sndMsgPanel.add(sent, BorderLayout.NORTH);
        sndMsgPanel.add(outPane, BorderLayout.CENTER);
        sndMsgPanel.add(sendPanel, BorderLayout.SOUTH);
        
        rcvMsgPanel.setLayout(new BorderLayout());
        rcvMsgPanel.add(received, BorderLayout.NORTH);
        rcvMsgPanel.add(inPane, BorderLayout.CENTER);
        
        messagePanel.add(sndMsgPanel);
        messagePanel.add(rcvMsgPanel);
        lowerPanel.add(messagePanel, BorderLayout.SOUTH);
        
        
        connectButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                String command = connectButton.getText();
                switch(command) {
                    case "Connect...":
                        if(cd == null) 
                            cd = new ConnectDialog(GameFrame.this);
                        cd.setVisible(true);
                        // hand over control to dialog for a moment
                        break;
                    case "Disconnect":
                        // close connection
                        try { 
                            outstr.writeObject( new Message("q", "quit") );
                        } catch(Exception e) { textIn.append(e.toString()); }
                        
                        break;
                }
            }
        });
        
        sendButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                try {
                    outstr.writeObject( new Message("c", toSend.getText() + "\n") );
                    textOut.append(toSend.getText() + "\n");
                } 
                catch(Exception exc) { textIn.append(exc.toString()); }
                
                toSend.setText("");                 // clear text input area
            }
        });
    }
    
    
     // Dialog box to serve/join a game
    class ConnectDialog extends JDialog {
        JTextField address;
        JPanel bPanel;
        JButton serve, join;
                
        public ConnectDialog(GameFrame owner) {
            super(owner, "Connect", true);

            setSize(250, 150);

            add(new JLabel("Enter an address:"), BorderLayout.NORTH);

            address = new JTextField(30);
            add(address, BorderLayout.CENTER);

            serve = new JButton("Serve game");
            join = new JButton("Join game");

            bPanel = new JPanel();
            bPanel.add(serve);
            bPanel.add(join);
            add(bPanel, BorderLayout.SOUTH);

            serve.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    setVisible(false);
                    Server();
                }
            });

            join.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    //addr = address.getText();
                    Client();
                    setVisible(false);
                }
            });
        }
    }
    
    
    // grid classes
    class GridPanel extends JPanel {        // in order to get coordinates from click
        int r, c;                           // each panel has a row & column #
        
        GridPanel(int r, int c) {
            super();
            this.r = r;
            this.c = c;
        }
    }
    
     // One grid, which is then extended for defend and attack grids
    class GameGrid extends JPanel {
        GridPanel[][] grid = new GridPanel[20][20];
        Border border;
        
        GameGrid(Color clr) {
            setSize(520, 520);
            setLayout(new GridLayout(21,21));
            JLabel label;
            border = BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1);

            // panels in the grid
            for(int i=0; i<20; i++) {
                // add a label first before each row of the grid
                Integer num = 20 - i;
                String s = num.toString();
                label = new JLabel(s);
                label.setHorizontalAlignment(JLabel.CENTER);
                add(label);
                
                // panels for each row
                for(int j=0; j<20; j++) {
                    grid[i][j] = new GridPanel(i,j);
                    grid[i][j].setBackground(clr);
                    grid[i][j].setBorder(border);
                    add(grid[i][j]);
                }
            }
            
            // bottom left corner with blank label
            add( new JLabel("") );

            // bottom row of labels
            for(int j=0; j<20; j++) {
                char c[] = new char[1];
                c[0] = (char) (j+65);
                label = new JLabel(  new String(c)  );
                label.setHorizontalAlignment(JLabel.CENTER);
                add(label);
            }
        }
    }
    
     // player 1 (defending) grid for defending ships
    class DGrid extends GameGrid {          
        
        DGrid(Color c) {
            super(c);
        }
    }
    
     // player 2 (attacking) grid to keep track of shots fired
    class AGrid extends GameGrid implements MouseListener {
        
        AGrid(Color c) {
            super(c);
            for(int i=0; i<20; i++)
                for(int j=0; j<20; j++)
                    grid[i][j].addMouseListener(this);
        }
        
        @Override
        public void mouseClicked(MouseEvent evt) {
            if(whosTurn == MINE) {
                GridPanel temp = (GridPanel) evt.getComponent();
                yCoord = temp.r;
                xCoord = temp.c;
                
                // send shoot message
                try {       
                    outstr.writeObject( new Message("s",yCoord, xCoord) );
                } 
                catch(Exception exc) { textIn.append(exc.toString()); }
                
                // switch to other player's turn
                whosTurn = THEIRS;
                connPanel.setBorder(red);
            }
        }
        
        public void mousePressed(MouseEvent me) { }
        public void mouseReleased(MouseEvent me) { }
        public void mouseEntered(MouseEvent me) { }
        public void mouseExited(MouseEvent me) { }
    }
    
    
     // Networking (no sender thread, only receiver thread)
    class Receiver implements Runnable {
        Thread rcvThr;
        Socket socket;
        ObjectInputStream instr;
        // ObjectOutputStream oustr previously declared
        
        Receiver(Socket s) {
            socket = s;
            rcvThr = new Thread(this);
            rcvThr.start();
        }
        
        public void run() {
            Message msg;
            
            try {
                outstr = new ObjectOutputStream(socket.getOutputStream());
                instr = new ObjectInputStream(socket.getInputStream());
                
                // Handle the various kinds of messages
                while(!done) {
                    msg = (Message) instr.readObject();           // get message
                    switch(msg.type) {
                        // chat message
                        case "c":
                            textIn.append( msg.str );
                            break;
                            
                        // shoot message
                        case "s":
                            shotResult = myShipHit(msg.r, msg.c);
                            try {
                                outstr.writeObject( new Message("r",shotResult) );
                                
                                if(allSunk()) 
                                    outstr.writeObject( new Message("c", "You sunk all "
                                        + "of my ships!\n") );
                            } 
                            catch(Exception exc) { textIn.append(exc.toString()); }
                            
                            whosTurn = MINE;
                            connPanel.setBorder(green);
                            break;
                            
                        // result message
                        case "r":
                            markShot( msg.hom );
                            break;
                        
                        // quit message
                        case "q":
                            if(msg.str.equals("quit")) {
                                textIn.append("   Opponent surrenders - game over\n");
                                
                                // acknowledge quit message with an OK
                                try { 
                                    outstr.writeObject( new Message("q", "okay") );
                                } catch(Exception e) { textIn.append(e.toString()); }
                            }
                            done = true;
                            break;
                            
                        // dice roll message
                        case "d":
                            // have the player who rolls 2nd decide who goes first
                            theirRoll = msg.roll;
                            textIn.append("   Opponent rolled " + theirRoll + "\n");
                            
                            if(myRoll == 0)           // if I haven't rolled yet
                                myRoll = rollD20();
                            whoisFirst(myRoll, theirRoll);
                            break;
                    }
                }
                textIn.append("   Closing connection...");
                
                instr.close();
                outstr.close();
                socket.close();
                
                textIn.append("connection closed\n");
                
            } catch(IOException exc) { 
                System.out.println("i/o error - host disconnected"); 
            } catch(ClassNotFoundException exc) {
                System.out.println("invalid object type");
            } 
            finally {
                resetGame();                    // clean up for next game
            }
            
        }
    }
    
    // Create socket connection for server
    public void Server() {
        ServerSocket server;
        Socket conn;
        
        try {
            server = new ServerSocket(port);
            
            status.setText("Waiting for client to connect");
            System.out.println("created new server socket");
            
            // would prefer temp thread so GUI doesn't hang while waiting for client
            conn = server.accept();
            
            msgReceiver = new Receiver(conn);
            textIn.append("   Server running\n");
                
            putShips.setEnabled(false); 
            sendButton.setEnabled(true);
            rollDice.setEnabled(true);
            connectButton.setText("Disconnect");
            
            host = conn.getInetAddress();
            status.setText("Connected to: " + host.getHostAddress());

        } catch (UnknownHostException exc) {
            status.setText("unknown host or invalid address\n");
        } catch (SocketException exc) {
            status.setText("socket error - couldn't connect to server\n");
        } catch (IOException exc) {
            status.setText("I/O error\n");
        } 
//        catch (Exception exc) {
//            exc.printStackTrace();
//        }
    }
    
    // Create socket connection for client
    public void Client() {
        Socket conn;
        
        try {
            host = InetAddress.getLocalHost();
            conn = new Socket(host, port);
            msgReceiver = new Receiver(conn);          // create receiver thread
            
            textIn.append("   Client running\n");
            
            putShips.setEnabled(false); 
            sendButton.setEnabled(true);
            rollDice.setEnabled(true);

            connectButton.setText("Disconnect");
            status.setText("Connected to: " + host.getHostAddress());
            
        } catch (UnknownHostException exc) {
            status.setText("unknown host or invalid address\n");
        } catch (SocketException exc) {
            status.setText("socket error - couldn't connect to server\n");
        } catch (IOException exc) {
            status.setText("I/O error\n");
        } 
//        catch (Exception exc) {
//            exc.printStackTrace();
//        }
    }
    
    
     // ship methods
    private boolean validPlace(Ship s) {
        if(s.horiz && s.col <= (20 - s.size)) {
           for(int c=0; c<s.size; c++) 
               if(defendPanel.grid[s.row][s.col + c].getBackground().equals(Color.DARK_GRAY))
                   return false;
           return true;
        }
        else if(!s.horiz && s.row <= (20 - s.size)) {
            for(int r=0; r<s.size; r++) 
               if(defendPanel.grid[s.row + r][s.col].getBackground().equals(Color.DARK_GRAY))
                   return false;
           return true;
        }
        return false;
    }
    
    private void addToBoard(Ship s) {
        if(s.horiz) {
           for(int c=0; c<s.size; c++) 
               defendPanel.grid[s.row][s.col + c].setBackground(Color.DARK_GRAY);
        }          
        else {
            for(int r=0; r<s.size; r++) 
               defendPanel.grid[s.row + r][s.col].setBackground(Color.DARK_GRAY);
        }           
    }
    
    private Ship buildShip(String type) {
        short size;
        boolean horiz;
        int row, col;
        
        switch(type) {
            case "Aircraft Carrier":
                size = 5; break;
            case "Battleship":
                size = 4; break;
            case "Destroyer":
            case "Submarine":
                size = 3; break;
            default:
                size = 2; break;
        }
        
        horiz = (Math.random() < .5);
        row = (int) (Math.random() * 20);
        col = (int) (Math.random() * 20);
        
        Ship s = new Ship(type, size, row, col, horiz);
        
        while(!validPlace(s)) {                             // find a valid spot
            s.row = (int) (Math.random() * 20);
            s.col = (int) (Math.random() * 20);
        }
        addToBoard(s);
        return s;
    }
    
    private void addAllShips() {
        fleet[0] = buildShip("Aircraft Carrier");
        fleet[1] = buildShip("Battleship");
        fleet[2] = buildShip("Battleship");
        fleet[3] = buildShip("Destroyer");
        fleet[4] = buildShip("Destroyer");
        fleet[5] = buildShip("Submarine");
        fleet[6] = buildShip("Submarine");
        for(int i=7; i<11; i++)
            fleet[i] = buildShip("Patrol Boat");
    }
    
    private boolean shipsPlaced() {
        for(Ship s : fleet)
            if(s == null) return false;
        return true;
    }
    
    private void clearShips() {
        for(int i=0; i<20; i++) {
            for(int j=0; j<20; j++) {
                defendPanel.grid[i][j].setBackground(ocean);
            }
        }
        for(int i=0; i<fleet.length; i++)
            fleet[i] = null;
    }
    
    private boolean myShipHit(int r, int c) {
        if(defendPanel.grid[r][c].getBackground().equals(Color.DARK_GRAY)) {
            defendPanel.grid[r][c].setBackground(Color.RED);
            hitCount++;
            return true;
        }
        else if(defendPanel.grid[r][c].getBackground().equals(Color.RED)) {
            // redundant hit!
            try {
                outstr.writeObject( new Message("c", "You already hit there!\n") );
            } 
            catch (IOException exc) {  textIn.append(exc.toString());  }
        }
        return false;
    }
    
    private boolean allSunk()
    {   return hitCount == 33;  }               // 33 hits to sink all 11 ships
    
    private void markShot(boolean hitormiss) {
        // mark attack square red if hit, ocean if miss
        if (hitormiss) {
            attackPanel.grid[yCoord][xCoord].setBackground(Color.RED);
            textIn.append("HIT\n");
        } else {
            attackPanel.grid[yCoord][xCoord].setBackground(ocean);
            textIn.append("MISS\n");
        }
    }
    
    
     // methods to decide starting player
    private short rollD20() {                               // roll 20-sided die
        short r = (short) ( Math.random() * 20 + 1);
        textIn.append("   You rolled " + r + "\n");
        
        try {   // send value so other player knows what I rolled
            outstr.writeObject( new Message("d", r) );
        } 
        catch (Exception exc) {  textIn.append(exc.toString());  }
        
        return r;
    }
    
    private void whoisFirst(short myroll, short theirs) {
        if (myroll > theirs) {
            whosTurn = MINE;
            rollDice.setEnabled(false);         // disable dice rolling
            connPanel.setBorder(green);
            textIn.append("   You go first\n");
        } else if (myroll < theirs) {
            // whosTurn = THEIRS;
            rollDice.setEnabled(false);
            textIn.append("   They go first\n");
        } else {                                // reset results - roll again
            myRoll = 0;
            theirRoll = 0;
            textIn.append("   Roll again\n");
        }
    }
    
     // After game is over...
    private void resetGame() {
        sendButton.setEnabled(false);
        connectButton.setEnabled(false);
        connectButton.setText("Connect...");
        status.setText("Disconnected");
        rollDice.setEnabled(false);
        putShips.setEnabled(true);
        connPanel.setBorder(red);
        whosTurn = THEIRS;
        myRoll = 0; theirRoll = 0;
        port++;                             // use different port next game
        
        // clear hits/misses on opponent grid
        for(int i=0; i<20; i++) {
            for(int j=0; j<20; j++) {
                attackPanel.grid[i][j].setBackground(Color.DARK_GRAY);
            }
        }
        
        clearShips();                       // clear ships/hits/sinks on my grid
        
        try { Thread.sleep(200); } 
        catch(InterruptedException e) 
            { System.out.println("interrupted"); }
        
        done = false;   // reset for next connection
    }
    
}

 // Other classes
class Ship {
    String type;        // e.g. submarine
    short size;         // length of ship
    int row,col;        // origin
    boolean horiz;      // orientation
    
    Ship(String t, short sz, int r, int c, boolean h) {
        type = t;   size = sz;   row = r;   col = c;   horiz = h;
    }
}

class Message implements Serializable {
    String type;
    short roll;
    String str;
    int r,c;
    boolean hom;
    
    Message(String t, String s) {               // several constructors for 
        type = t;                               // different kinds of messages
        str = s;
    }
    Message(String t, int y, int x) {
        type = t;
        r = y;
        c = x;
    }
    Message(String t, boolean h) {
        type = t;
        hom = h;
    }
    Message(String t, short val) {
        type = t;
        roll = val;
    }
}


public class BattleshipGame {
    public static void main(String[] args) {
        EventQueue.invokeLater( new Runnable() {
            public void run() {
                GameFrame frame = new GameFrame();
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setVisible(true);
            }
        });
    }
}
