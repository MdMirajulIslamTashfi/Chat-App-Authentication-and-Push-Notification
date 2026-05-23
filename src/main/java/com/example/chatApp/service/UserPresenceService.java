package com.example.chatApp.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserPresenceService {
    private final Set<String> onlineUsers = ConcurrentHashMap.newKeySet();

    public void setOnline(String email){ onlineUsers.add(email); }
    public void setOffline(String email){ onlineUsers.remove(email); }
    public boolean isOnline(String email){ return onlineUsers.contains(email); }
    public Set<String> getOnlineUsers() { return Collections.unmodifiableSet(onlineUsers); }
}
