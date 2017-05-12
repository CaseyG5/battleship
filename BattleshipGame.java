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
    public static final boolean HIT = true;
    public static final boolean MISS = false;
    
    JMenuBar menuBar;
    JMenu gameMenu;
    JMenu putShips;
    JMenu rollDice;
    
    JPanel gamePanel;
    DGrid defendPanel; 
    AGrid attackPanel;
    
    JPanel lowerPanel;
    JPanel connPanel;
    JButton connectButton;
    JLabel status;
    
    ConnectDialog cd;
    
    JPanel messagePanel;
    JPanel sndMsgPanel, rcvMsgPanel;
    JPanel sendPanel;
    JLabel sent, received;
    JScrollPane outPane, inPane;
    JTextArea textOut, textIn;  // read only
    JTextField toSend;
    JButton sendButton;
    Color ocean;
    Border green, red;
    
    private int xCoord;
    private int yCoord;
    
    public Ship[] fleet;
    
    public int port = 1776;
    public String addr;
    public InetAddress host;
    
    Sender msgSender;
    Receiver msgReceiver;
    
    boolean connected = false;
    
    public boolean done = false;
    
    short myRoll, theirRoll;
    boolean whosTurn = THEIRS;
    boolean shotResult = MISS;
    short hitCount;
    
    public GameFrame() {
        setTitle("Battleships");
        setSize(TOTAL_WIDTH, TOTAL_HEIGHT);
        setLayout(new BorderLayout());
        
        ocean = new Color(90,180,250);
        green = BorderFactory.createLineBorder(Color.GREEN, 7);
        red = BorderFactory.createLineBorder(Color.RED, 7);
        fleet = new Ship[10];
        
        // menus
        menuBar = new JMenuBar();
        setJMenuBar(menuBar);
        
        gameMenu = new JMenu("Game");
        
        putShips = new JMenu("Place Ships");
//            JMenuItem acc = new JMenuItem("Aircraft Carrier");
//            JMenuItem bship = new JMenuItem("Battleship");
//            JMenuItem dstr = new JMenuItem("Destroyer");
//            JMenuItem sub = new JMenuItem("Submarine");
//            JMenuItem pb = new JMenuItem("Patrol Boat");
            
//            putShips.add(acc);
//            putShips.add(bship);
//            putShips.add(dstr);
//            putShips.add(sub);
//            putShips.add(pb);
//            putShips.addSeparator();

            putShips.add( new AbstractAction("All Randomly") {
                public void actionPerformed(ActionEvent evt) {
                    addAllShips();
                    connectButton.setEnabled(true);
                    //textOut.append("Ready\n");
                }
            });
            putShips.addSeparator();
            putShips.add( new AbstractAction("Clear All") {
                public void actionPerformed(ActionEvent evt) {
                    clearShips();
                    connectButton.setEnabled(false);
                    //textOut.append("Not ready\n");
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
        
        // north panel to keep track of game state
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
        
        // center panel to connect/disconnect
        connPanel = new JPanel();
        connPanel.setBorder(red);
        
        connectButton = new JButton("Connect...");
        connectButton.setEnabled(false);
        status = new JLabel("not connected");
        
        connPanel.add(connectButton);
        connPanel.add(status);
        lowerPanel.add(connPanel, BorderLayout.NORTH);
        
        // south panel for messaging
        messagePanel = new JPanel();
        messagePanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 5));
        messagePanel.setLayout(new GridLayout(1,2));
        
        // 
        sndMsgPanel = new JPanel();
        rcvMsgPanel = new JPanel();
        sendPanel = new JPanel();
        
        sent = new JLabel("Sent messages:");
        received = new JLabel("Received messages:");
        textOut = new JTextArea(5,30);
          textOut.setEditable(false);
        
          
        outPane = new JScrollPane(textOut);
        outPane.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 3));
        outPane.setViewportView(textOut);       // ?? does it work??
        
        textIn = new JTextArea(6,30);
          textIn.setEditable(false);
          
        inPane = new JScrollPane(textIn);
        inPane.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 3));
          
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
                        msgSender.msg = new Message("c", "quit");
                        done = true;
                        connected = false;
                        sendButton.setEnabled(false);
                        defendPanel.setEnabled(true);
                        putShips.setEnabled(true);
                        rollDice.setEnabled(false);
                        connectButton.setText("Connect...");
                        status.setText("Disconnected");
                        connPanel.setBorder(red);
                        try { Thread.sleep(1000); } catch(InterruptedException e) { }
                        done = false;   // reset for next connection
                        break;
                }
            }
        });
        
        sendButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                //try {
                    // leave a new chat message for sender to send
                    msgSender.msg = new Message("c", toSend.getText() + "\n");
                    
                    textOut.append("\"" + toSend.getText() + "\"\n");
                //} catch(IOException exc) { textOut.append(exc.toString()); }
                
                toSend.setText("");
            }
        });
    }
    
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
        GridPanel[][] grid = new GridPanel[25][25];
        Border border;
        
        GameGrid(Color clr) {
            setSize(520, 520);
            setLayout(new GridLayout(26,26));
            JLabel label;
            border = BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1);

            // panels in the grid
            for(int i=0; i<25; i++) {
                // add a label first before each row of the grid
                Integer num = 25 - i;
                String s = num.toString();
                label = new JLabel(s);
                label.setHorizontalAlignment(JLabel.CENTER);
                add(label);
                
                // panels for each row
                for(int j=0; j<25; j++) {
                    grid[i][j] = new GridPanel(i,j);
                    grid[i][j].setBackground(clr);
                    grid[i][j].setBorder(border);
                    add(grid[i][j]);
                }
            }
            
            // bottom left corner with blank label
            add( new JLabel("") );

            // bottom row of labels
            for(int j=0; j<25; j++) {
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
    
     // player 2 (attacking) grid  to keep track of shots fired
    class AGrid extends GameGrid implements MouseListener {
        
        AGrid(Color c) {
            super(c);
            for(int i=0; i<25; i++)
                for(int j=0; j<25; j++)
                    grid[i][j].addMouseListener(this);
        }
        
        @Override
        public void mouseClicked(MouseEvent evt) {
            if(whosTurn == MINE) {
                GridPanel temp = (GridPanel) evt.getComponent();
                yCoord = temp.r;
                xCoord = temp.c;

                // leave a new shoot message for sender to send
                msgSender.msg = new Message("s",yCoord, xCoord);
                // switch to other player's turn
                whosTurn = THEIRS;
            }
        }
        
        public void mousePressed(MouseEvent me) { }
        public void mouseReleased(MouseEvent me) { }
        public void mouseEntered(MouseEvent me) { }
        public void mouseExited(MouseEvent me) { }
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
                    try {
                        setVisible(false);
                        Server();
                    } catch(UnknownHostException exc) { 
                        status.setText("unknown host or invalid address\n");
                    } 
                    catch(IOException exc) { status.setText("I/O error\n"); }
                    catch(Exception exc)   { exc.printStackTrace(); }
                    
                }
            });

            join.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    //addr = address.getText();
                    try {
                        Client();
                        
                    } catch(UnknownHostException exc) { 
                        status.setText("unknown host or invalid address\n");
                    } catch(SocketException exc) { 
                        status.setText("socket error - couldn't connect to server\n"); 
                    }
                    catch(IOException exc) { status.setText("I/O error\n"); }
                    catch(Exception exc)   { exc.printStackTrace(); }
                    
                    setVisible(false);
                }
            });
        }
    }
    
     // Networking
    class Sender implements Runnable {
        Thread sndThr;
        Socket socket;
        ObjectOutputStream outstr;
        Message msg;
        
        Sender(Socket s) {
            socket = s;
            sndThr = new Thread(this);
            sndThr.start();
        }
        
        public void run() {
            try {
                outstr = new ObjectOutputStream(socket.getOutputStream());
                //textOut.append("object output stream created\n");
                
                while(!done) { 
                    //this.wait();
                    if(msg != null) {
                        outstr.writeObject(msg);
                        msg = null;
                    }
                    try { Thread.sleep(100);  } catch(InterruptedException e) {}
                }
                outstr.close();
                
             } catch(IOException exc) { textOut.append("i/o error\n"); }
            catch(Exception exc) { textOut.append("something went wrong\n"); }
        }
    }
    
    class Receiver implements Runnable {
        Thread rcvThr;
        Socket socket;
        ObjectInputStream instr;
        ObjectOutputStream outstr;
        
        Receiver(Socket s, ObjectOutputStream oos) {
            socket = s;
            outstr = oos;
            rcvThr = new Thread(this);
            rcvThr.start();
        }
        
        public void run() {
            Message msg;
            
            try {
                instr = new ObjectInputStream(socket.getInputStream());
                //textOut.append("object input stream created\n");
                
                msg = (Message) instr.readObject();            // get first message
                
                while(!done) {
                    switch(msg.type) {
                        // chat message
                        case "c":
                            textIn.append( msg.str );
                            if(msg.str.equals("quit")) done = true;
                            break;
                            
                        // shoot message
                        case "s":
                            int r = msg.r;
                            int c = msg.c;
                            shotResult = myShipHit(r,c);
                            // leave new result message for sender to send
                            msgSender.msg = new Message("r",shotResult);
                            if(allSunk()) {
                                // leave new chat (win-lose) message for sender to send
                                Thread.sleep(250);
                                msgSender.msg = new Message("c", "You sunk all of my ships!\n");
                            }
                            whosTurn = MINE;
                            
                            // make some green highlight
                            break;
                            
                        // result message
                        case "r":
                            // mark attack square red if hit, ocean if miss
                            if( msg.hom == HIT )
                                attackPanel.grid[yCoord][xCoord].setBackground(Color.RED);
                            else
                                attackPanel.grid[yCoord][xCoord].setBackground(ocean);
                            break;
                            
                        // dice roll message
                        case "d":
                            // have the player who rolls 2nd decide who goes first
                            theirRoll = msg.roll;
                            
                            if(myRoll == 0)           // if I haven't rolled yet
                                myRoll = rollD20();
                            whoisFirst(myRoll, theirRoll);
                            break;
                    }
                    msg = (Message) instr.readObject();        // get next message
                }
                System.out.println("quitting receiver");
                
                instr.close();
                //notify();
                Thread.sleep(1000);
                socket.close();
                
            } catch(IOException exc) { 
                System.out.println("i/o error - host disconnected"); 
            } catch(ClassNotFoundException exc) {
                System.out.println("invalid object type");
            } catch(InterruptedException exc) {
                System.out.println("interrupted");
            }
        }
    }
    
    public void Server() throws Exception {
        ServerSocket server;
        Socket conn;
        server = new ServerSocket(port);
        System.out.println("created new server socket");
        status.setText("Waiting for client to connect");
        System.out.println("waiting for client to connect");
        
        // need temp thread so gui doesn't hang?
            try {  
                conn = server.accept();
                connected = true;
                putShips.setEnabled(false); 
                sendButton.setEnabled(true);
                rollDice.setEnabled(true);
                
                connectButton.setText("Disconnect");
                host = conn.getInetAddress();
                status.setText("Connected to: " + host.getHostAddress());

                msgSender = new Sender(conn);
                msgReceiver = new Receiver(conn, msgSender.outstr);
                //textOut.append("Server running\n");
                connPanel.setBorder(green);
            }
            catch(IOException exc) 
            {   status.setText("Could not make a connection\n");   }
    }
    
    public void Client() throws Exception {
        Socket conn;
        
        //host = InetAddress.getByName(addr);
        host = InetAddress.getLocalHost();
        
        conn = new Socket(host, port);
        
        connected = true;
        
        //textOut.append("client address: " + host.getHostAddress() + "\n");
        
        putShips.setEnabled(false); 
        sendButton.setEnabled(true);
        rollDice.setEnabled(true);
        
        connectButton.setText("Disconnect");
        status.setText("Connected to: " + host.getHostAddress());
        
        msgSender = new Sender(conn);
        msgReceiver = new Receiver(conn, msgSender.outstr);
        //textOut.append("Client running\n");
        connPanel.setBorder(green);
    }
    
    
    // ship methods
    private boolean validPlace(Ship s) {
        if(s.horiz && s.col <= (25 - s.size)) {
           for(int c=0; c<s.size; c++) 
               if(defendPanel.grid[s.row][s.col + c].getBackground().equals(Color.DARK_GRAY))
                   return false;
           return true;
        }
        else if(!s.horiz && s.row <= (25 - s.size)) {
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
    
    private Ship buildShip(String n) {
        short size;
        boolean horiz;
        int row, col;
        
        switch(n) {
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
        row = (int) (Math.random() * 25);
        col = (int) (Math.random() * 25);
        
        Ship s = new Ship(n, size, row, col, horiz);
        while(!validPlace(s)) {
            row = (int) (Math.random() * 25);
            col = (int) (Math.random() * 25);
            s.row = row;
            s.col = col;
        }
        addToBoard(s);
        return s;
    }
    
    private void addAllShips() {
        fleet[0] = buildShip("Aircraft Carrier");
        fleet[1] = buildShip("Battleship");
        fleet[2] = buildShip("Destroyer");
        fleet[3] = buildShip("Destroyer");
        fleet[4] = buildShip("Submarine");
        fleet[5] = buildShip("Submarine");
        for(int i=6; i<10; i++)
            fleet[i] = buildShip("Patrol Boat");
    }
    
    private boolean shipsPlaced() {
        for(Ship s : fleet)
            if(s == null) return false;
        return true;
    }
    
    private void clearShips() {
        for(int i=0; i<25; i++) {
            for(int j=0; j<25; j++) {
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
            msgSender.msg = new Message("c", "You already hit there!");
            //notify();  doesn't work this way
        }
        return false;
    }
    
    private boolean allSunk()
    {   return hitCount == 29;  }
    
    private short rollD20() {                               // roll 20-sided die
        short r = (short) ( Math.random() * 20 + 1);
        textOut.append("You rolled a " + r + "\n");
        msgSender.msg = new Message("d", r);
        return r;
    }
    
    private void whoisFirst(short myroll, short theirs) {
        if (myroll > theirs) {
            whosTurn = MINE;
            rollDice.setEnabled(false);
        } else if (myroll < theirs) {
            //whosTurn = THEIRS;
            rollDice.setEnabled(false);
        } else {                                // reset results - roll again
            myRoll = 0;
            theirRoll = 0;
        }
    }
    
}

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
    
    Message(String t, String s) {
        type = t;
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
                frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                frame.setVisible(true);
            }
        });
        
    }
    
}
