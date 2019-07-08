package mytest;

import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class NIOZeroCopy {

    public static void setUp() throws IOException {
        int _1mb = 1024 * 1024 * 1;
        int fileSize = _1mb * 1; // 1mb
        StringBuilder sb = new StringBuilder();
        String unit = "01234567890\n";
        while (sb.length() < _1mb) {
            sb.append(unit);
        }
        new File("tmp").delete();
        while (new File("tmp").length() < fileSize) {
            FileUtils.writeStringToFile(new File("tmp"), sb.toString(), true);
        }
        System.out.println("write file done");
    }

    static class MyThread extends Thread {
        @Override
        public synchronized void run() {
            try {
                ServerSocket serverSocket = new ServerSocket(9999);
                serverSocket.setReuseAddress(true);
                Socket accept = serverSocket.accept();
                SocketChannel channel = accept.getChannel();
                ByteBuffer allocate = ByteBuffer.allocate(1024);
                while (true) {
                    channel.read(allocate);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void pleaseStop() {

        }
    }

    public static void startLocalServer() {
        System.out.println("startLocalServer start");

        Thread thread = new Thread() {
            @Override
            public synchronized void run() {
                try {

                    ServerSocketChannel listener = ServerSocketChannel.open();
                    ServerSocket socket = listener.socket();
                    socket.setReuseAddress(true);
                    socket.bind(new InetSocketAddress(9999));
                    ByteBuffer buffer = ByteBuffer.allocate(4096);
                    long total = 0;
                    while (true) {
                        SocketChannel channel = listener.accept();
                        int read = 0;
                        while(read!=-1){
                            try {
                                read = channel.read(buffer);
                                total+=read;
                            } catch (IOException e) {
                                e.printStackTrace();
                                read = -1;
                            }
                            buffer.rewind();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }


        };
        thread.setDaemon(true);
        thread.start();
        System.out.println("startLocalServer done");
    }

    public static void traditional(FileInputStream fis, Socket localSocket, long size) throws IOException {
        byte[] buffer = new byte[4096];
        int count = -1;
        int read = 0;
        localSocket.setSoTimeout(1000);
        OutputStream outputStream = new DataOutputStream(localSocket.getOutputStream());
        while ((count = fis.read(buffer)) != -1) {
            read+=count;
            if(read>size){
                throw new RuntimeException("bad!");
            }
            outputStream.write(buffer);
        }
        outputStream.close();
    }

    public static void zeroCopy(FileInputStream fis, SocketChannel channel, long size) throws IOException {
        FileChannel channel1 = fis.getChannel();
        channel1.transferTo(0, size, channel);
        channel1.close();
    }


    public static void main(String[] args) throws IOException {
        setUp();
        startLocalServer();
        System.out.println("file size: " + new File("tmp").length());

        long total = 0;


        total = 0;
        File file = new File("tmp");
        long size = file.length();
        int TOTAL_ROUNDS = 1;
        for (int i = 0; i < TOTAL_ROUNDS; i++) {
            FileInputStream tmp = new FileInputStream(file);
            InetSocketAddress localhost1 = new InetSocketAddress("localhost", 9999);
            SocketChannel writableChannel = SocketChannel.open();
            writableChannel.connect(localhost1);
            long start = System.currentTimeMillis();
            zeroCopy(tmp, writableChannel, size);
            long end = System.currentTimeMillis();
            long spent = end - start;
            total += spent;
            tmp.close();
            writableChannel.close();
        }
        System.out.println("avg zero copy: " + ((total + 0.0) / 100));

        total = 0;
        for (int i = 0; i < TOTAL_ROUNDS; i++) {
            FileInputStream fis = new FileInputStream(file);
            Socket socketClient = new Socket("localhost", 9999);
            long start = System.currentTimeMillis();
            traditional(fis, socketClient, size);
            long end = System.currentTimeMillis();
            long spent = end - start;
            total += spent;
            fis.close();
            socketClient.close();
        }
        System.out.println("avg traditional : " + ((total+0.0)/100));

    }

}
