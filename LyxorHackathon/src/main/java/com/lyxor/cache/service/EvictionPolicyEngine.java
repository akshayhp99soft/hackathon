package com.lyxor.cache.service;

import java.util.HashMap;
import java.util.Map;

public class EvictionPolicyEngine<K, V> {

    static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private final int maxCapacity;
    private final Map<K, Node<K, V>> indexMap = new HashMap<>();
    private Node<K, V> head;
    private Node<K, V> tail;

    public EvictionPolicyEngine(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public synchronized void put(K key, V value) {
        Node<K, V> existing = indexMap.get(key);
        if (existing != null) {
            existing.value = value;
            moveToHead(existing);
            return;
        }

        if (indexMap.size() >= maxCapacity) {
            evictTail();
        }

        Node<K, V> newNode = new Node<>(key, value);
        indexMap.put(key, newNode);
        addToHead(newNode);
    }

    public synchronized V get(K key) {
        Node<K, V> node = indexMap.get(key);
        if (node == null) {
            return null;
        }
        moveToHead(node);
        return node.value;
    }

    private void evictTail() {
        if (tail == null) return;
        indexMap.remove(tail.key);
        Node<K, V> oldTail = tail;
        if (tail.prev != null) {
            tail = tail.prev;
            tail.next = null;
        } else {
            head = null;
            tail = null;
        }
    }

    private void addToHead(Node<K, V> node) {
        node.next = head;
        node.prev = null;
        if (head != null) {
            head.prev = node;
        }
        head = node;
        if (tail == null) {
            tail = node;
        }
    }

    private void moveToHead(Node<K, V> node) {
        if (node == head) return;
        if (node.prev != null) node.prev.next = node.next;
        if (node.next != null) node.next.prev = node.prev;
        if (node == tail) tail = node.prev;

        addToHead(node);
    }

    public synchronized int size() {
        return indexMap.size();
    }
}
