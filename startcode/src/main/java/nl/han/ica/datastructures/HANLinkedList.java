package nl.han.ica.datastructures;

public class HANLinkedList<T> implements IHANLinkedList<T> {

    private static class Node<E> {
        E value;
        Node<E> next;
        Node(E value) { this.value = value; }
    }

    private final Node<T> header; // dummy header node (not counted in size)
    private int size;

    public HANLinkedList() {
        header = new Node<>(null);
        size = 0;
    }

    @Override
    public void addFirst(T value) {
        Node<T> node = new Node<>(value);
        node.next = header.next;
        header.next = node;
        size++;
    }

    @Override
    public void clear() {
        header.next = null;
        size = 0;
    }

    @Override
    public void insert(int index, T value) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        Node<T> prev = header;
        for (int i = 0; i < index; i++) {
            prev = prev.next;
        }
        Node<T> node = new Node<>(value);
        node.next = prev.next;
        prev.next = node;
        size++;
    }

    @Override
    public void delete(int pos) {
        if (pos < 0 || pos >= size) {
            throw new IndexOutOfBoundsException("Pos: " + pos + ", Size: " + size);
        }
        Node<T> prev = header;
        for (int i = 0; i < pos; i++) {
            prev = prev.next;
        }
        prev.next = prev.next.next;
        size--;
    }

    @Override
    public T get(int pos) {
        if (pos < 0 || pos >= size) {
            throw new IndexOutOfBoundsException("Pos: " + pos + ", Size: " + size);
        }
        Node<T> curr = header.next;
        for (int i = 0; i < pos; i++) {
            curr = curr.next;
        }
        return curr.value;
    }

    @Override
    public void removeFirst() {
        if (size == 0) return;
        header.next = header.next.next;
        size--;
    }

    @Override
    public T getFirst() {
        if (size == 0) return null;
        return header.next.value;
    }

    @Override
    public int getSize() {
        return size;
    }
}