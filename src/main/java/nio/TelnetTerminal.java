package nio;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

public class TelnetTerminal {

    private Path current;
    private ServerSocketChannel server;
    private Selector selector;
    private ByteBuffer buf;

    public TelnetTerminal() throws IOException {
        current = Path.of("src").toAbsolutePath();
        buf = ByteBuffer.allocate(256);
        server = ServerSocketChannel.open();
        selector = Selector.open();
        server.bind(new InetSocketAddress(8189));
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);

        while (server.isOpen()) {
            selector.select();
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectionKeys.iterator();
            while (keyIterator.hasNext()) {
                SelectionKey next = keyIterator.next();
                if (next.isAcceptable()) {
                    handleAccept();
                }
                if (next.isReadable()) {
                    handleRead(next);
                }
                keyIterator.remove();
            }
        }
    }

    private void handleRead(SelectionKey next) throws IOException {
        SocketChannel channel = (SocketChannel) next.channel();
//        channel.write(ByteBuffer.wrap(":~$".getBytes()));
        buf.clear();
        StringBuilder sb = new StringBuilder();
        while (true) {
            int read = channel.read(buf);
            if (read == 0) {
                break;
            }
            if (read == -1) {
                channel.close();
                return;
            }
            buf.flip();
            while (buf.hasRemaining()) {
                sb.append((char) buf.get());
            }
            buf.clear();
        }
        String command = sb.toString().trim();
        if (command.equals("ls")) {
            String files = Files.list(current)
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.joining("\n\r"));
            channel.write(ByteBuffer.wrap(files.getBytes(StandardCharsets.UTF_8)));
            channel.write(ByteBuffer.wrap("\n".getBytes()));
        } else if (command.contains(" ")) {
            String[] s = command.split(" ");
            if (s[0].equals("cd")) {
                if (s[1].equals("..")) {
                    if (!current.equals(current.getRoot())) {
                        current = current.getParent();
                    }
                } else {
                    Path cdPath = Path.of(current + "/" + s[1]);
                    if (Files.isDirectory(cdPath)) {
                        current = cdPath;
                    }
                }
                channel.write(ByteBuffer.wrap(("Вы перешли в директорию: " + current.toString()).getBytes(StandardCharsets.UTF_8)));
                channel.write(ByteBuffer.wrap("\n".getBytes()));
            } else if (s[0].equals("touch")) {
                File file = new File(String.valueOf(current), s[1]);
                if (!file.exists()) {
                    Files.createFile(file.toPath());
                    channel.write(ByteBuffer.wrap(("Вы создали файл: " + s[1]).getBytes(StandardCharsets.UTF_8)));
                    channel.write(ByteBuffer.wrap("\n".getBytes()));
                }
            } else if (s[0].equals("mkdir")) {
                Path dir = Path.of(current + "/" + Path.of(s[1]));
                if (!Files.isDirectory(dir)) {
                    Files.createDirectory(dir);
                    channel.write(ByteBuffer.wrap(("Вы создали папку: " + s[1]).getBytes(StandardCharsets.UTF_8)));
                    channel.write(ByteBuffer.wrap("\n".getBytes()));
                }
            } else if (s[0].equals("cat")) {
                if (Files.exists(Path.of(s[1]))) {
                    File file = new File(s[1]);
                    try (FileInputStream fis = new FileInputStream(file)){
                        while (fis.available() > 0) {
                            byte[] bytes = fis.readAllBytes();
                            channel.write(ByteBuffer.wrap("Байт код файла: ".getBytes()));
                            channel.write(ByteBuffer.wrap(Arrays.toString(bytes).getBytes()));
                            channel.write(ByteBuffer.wrap("\n".getBytes()));
                        }
                    }
                }
            }
        } else {
            byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            channel.write(ByteBuffer.wrap(bytes));
        }
    }

    private void handleAccept() throws IOException {
        SocketChannel accept = server.accept();
        accept.configureBlocking(false);
        accept.register(selector, SelectionKey.OP_READ);
        System.out.println("Client accepted");
    }

    public static void main(String[] args) throws IOException {
        new TelnetTerminal();
    }

}
