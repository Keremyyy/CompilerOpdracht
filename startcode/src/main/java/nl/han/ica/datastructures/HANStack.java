package nl.han.ica.datastructures;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EmptyStackException;

public class HANStack<T> implements IHANStack<T> {
    private final Deque<T> deque = new ArrayDeque<>();

    public void push(T value) {
        deque.addFirst(value);
    }

    public T pop() {
        if (deque.isEmpty()) throw new EmptyStackException();
        return deque.removeFirst();
    }

    public T peek() {
        if (deque.isEmpty()) throw new EmptyStackException();
        return deque.peekFirst();
    }
}