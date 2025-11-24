// Simple client for existing REST APIs
const DMS = (() => {
  const nodesEl = document.getElementById('nodes');
  const messagesEl = document.getElementById('messages');
  const refreshNodesBtn = document.getElementById('refresh-nodes');
  const refreshMessagesBtn = document.getElementById('refresh-messages');
  const refreshHeartbeatsBtn = document.getElementById('refresh-heartbeats');
  const refreshLeaderBtn = document.getElementById('refresh-leader');
  const refreshReplicasBtn = document.getElementById('refresh-replicas');
  const refreshReplicasManualBtn = document.getElementById('refresh-replicas-manual');
  const triggerElectionBtn = document.getElementById('trigger-election');
  const enablePartitionBtn = document.getElementById('enable-partition');
  const disablePartitionBtn = document.getElementById('disable-partition');
  const sendForm = document.getElementById('send-form');
  const sendStatus = document.getElementById('send-status');
  const senderSelect = document.getElementById('sender');
  const receiverSelect = document.getElementById('receiver');
  const healthEl = document.getElementById('health');
  
  // Get current server info from URL
  const currentServer = window.location.hostname + ':' + window.location.port;
  let currentServerId = 'server-' + window.location.port; // Default fallback

  async function refreshNodes() {
    try {
      refreshNodesBtn && (refreshNodesBtn.disabled = true);
      const res = await fetch('/admin/nodes');
      const nodes = await res.json();
      nodesEl.innerHTML = '';
      senderSelect.innerHTML = '<option value="">Select sender server...</option>';
      receiverSelect.innerHTML = '<option value="">Select receiver server...</option>';
      
      if (!nodes || nodes.length === 0) {
        nodesEl.innerHTML = '<div class="item">No active nodes</div>';
        return;
      }
      
      for (const n of nodes) {
        // Display nodes in the list
        const div = document.createElement('div');
        div.className = 'item';
        const nodeId = n.id || `${n.host}:${n.port}`;
        const isCurrentServer = nodeId === currentServerId || `${n.host}:${n.port}` === currentServer;
        
        // Update currentServerId if this is the current server
        if (isCurrentServer && n.id) {
          currentServerId = n.id;
          console.log('Updated currentServerId to:', currentServerId);
        }
        
        // Highlight current server
        if (isCurrentServer) {
          div.style.border = '2px solid #22c55e';
          div.style.backgroundColor = 'rgba(34, 197, 94, 0.1)';
        }
        
        div.textContent = `${nodeId} — ${n.host || ''}:${n.port || ''}${isCurrentServer ? ' (THIS SERVER)' : ''}`;
        nodesEl.appendChild(div);
        
        // Add to sender dropdown
        const senderOption = document.createElement('option');
        senderOption.value = nodeId;
        senderOption.textContent = `${nodeId} (${n.host}:${n.port})`;
        if (isCurrentServer) {
          senderOption.selected = true; // Auto-select current server as sender
        }
        senderSelect.appendChild(senderOption);
        
        // Add to receiver dropdown (exclude current server)
        if (!isCurrentServer) {
          const receiverOption = document.createElement('option');
          receiverOption.value = nodeId;
          receiverOption.textContent = `${nodeId} (${n.host}:${n.port})`;
          receiverSelect.appendChild(receiverOption);
        }
      }
      
      // Add broadcast option to receiver dropdown
      const broadcastOption = document.createElement('option');
      broadcastOption.value = 'BROADCAST';
      broadcastOption.textContent = '📢 Broadcast to All Servers';
      receiverSelect.appendChild(broadcastOption);
    } catch (e) {
      nodesEl.innerHTML = `<div class="item">Error loading nodes</div>`;
    } finally {
      refreshNodesBtn && (refreshNodesBtn.disabled = false);
      // Refresh messages after updating server ID to ensure proper filtering
      refreshMessages();
    }
  }

  async function refreshMessages() {
    try {
      refreshMessagesBtn && (refreshMessagesBtn.disabled = true);
      const res = await fetch('/admin/messages');
      const msgs = await res.json();
      messagesEl.innerHTML = '';
      if (!msgs || msgs.length === 0) {
        messagesEl.innerHTML = '<div class="item">No messages yet</div>';
        return;
      }
      for (const m of msgs) {
        const ts = m.timestamp || '';
        const senderId = m.sender || 'unknown';
        const receiverId = m.receiver || 'all';
        
        // Debug logging
        console.log('Message:', m.id, 'originNodeId:', m.originNodeId, 'currentServerId:', currentServerId, 'receiverId:', receiverId, 'senderId:', senderId);
        
        // Determine message direction from current server's perspective
        // Check if this message was sent by the current server (by checking originNodeId)
        const isSent = m.originNodeId === currentServerId;
        // Check if this message was received by the current server
        // For BROADCAST: all servers receive it
        // For unicast: receiver should match current server ID
        const isReceived = receiverId === 'BROADCAST' || receiverId === currentServerId;
        
        console.log('Message filtering - isSent:', isSent, 'isReceived:', isReceived, 'willShow:', (isSent || isReceived));
        
        // Only show messages that are sent by this server, received by this server, or broadcast messages
        if (!isSent && !isReceived) {
          console.log('Filtering out message:', m.id, 'isSent:', isSent, 'isReceived:', isReceived);
          continue;
        }
        
        console.log('Showing message:', m.id, 'isSent:', isSent, 'isReceived:', isReceived);
        
        const div = document.createElement('div');
        div.className = 'item';
        
        let directionText = '';
        let messageClass = '';
        
        if (isSent && isReceived) {
          directionText = '📤📥 SENT & RECEIVED';
          messageClass = 'sent-received';
        } else if (isSent) {
          directionText = '📤 SENT';
          messageClass = 'sent';
        } else if (isReceived) {
          directionText = '📥 RECEIVED';
          messageClass = 'received';
        }
        
        // Handle broadcast messages
        const displayReceiver = receiverId === 'BROADCAST' ? '📢 ALL SERVERS' : receiverId;
        
        div.className = `item ${messageClass}`;
        div.innerHTML = `<div><strong>${directionText}</strong></div>
                         <div><strong>From:</strong> ${senderId} → <strong>To:</strong> ${displayReceiver}</div>
                         <div><strong>Origin Node:</strong> ${m.originNodeId || 'unknown'}</div>
                         <div><strong>Lamport Clock:</strong> ${m.lamport || 'N/A'}</div>
                         <div>${escapeHtml(m.payload || '')}</div>
                         <small style="color:#9ca3af">${ts}</small>`;
        messagesEl.appendChild(div);
      }
    } catch (e) {
      messagesEl.innerHTML = `<div class="item">Error loading messages</div>`;
    } finally {
      refreshMessagesBtn && (refreshMessagesBtn.disabled = false);
    }
  }

  async function refreshHeartbeats() {
    try {
      refreshHeartbeatsBtn && (refreshHeartbeatsBtn.disabled = true);
      const res = await fetch('/admin/heartbeats');
      const beats = await res.json();
      healthEl.innerHTML = '';
      if (!beats || beats.length === 0) {
        healthEl.innerHTML = '<div class="item">No heartbeats</div>';
        return;
      }
      for (const b of beats) {
        const div = document.createElement('div');
        div.className = 'item';
        div.textContent = `❤️ ${b}`;
        healthEl.appendChild(div);
      }
    } catch (e) {
      healthEl.innerHTML = '<div class="item">Error loading heartbeats</div>';
    } finally {
      refreshHeartbeatsBtn && (refreshHeartbeatsBtn.disabled = false);
    }
  }

  async function refreshLeader() {
    try {
      refreshLeaderBtn && (refreshLeaderBtn.disabled = true);
      const res = await fetch('/admin/leader');
      const leader = await res.text();
      const div = document.createElement('div');
      div.className = 'item';
      div.textContent = `👑 Leader: ${leader || 'unknown'}`;
      healthEl.appendChild(div);
    } catch (e) {
      const div = document.createElement('div');
      div.className = 'item';
      div.textContent = 'Error loading leader';
      healthEl.appendChild(div);
    } finally {
      refreshLeaderBtn && (refreshLeaderBtn.disabled = false);
    }
  }

  async function refreshReplicas() {
    try {
      refreshReplicasBtn && (refreshReplicasBtn.disabled = true);
      const res = await fetch('/admin/replicas');
      const reps = await res.json();
      const div = document.createElement('div');
      div.className = 'item';
      div.textContent = `🔗 Replicas: ${Array.isArray(reps) ? reps.join(', ') : 'unknown'}`;
      healthEl.appendChild(div);
    } catch (e) {
      const div = document.createElement('div');
      div.className = 'item';
      div.textContent = 'Error loading replicas';
      healthEl.appendChild(div);
    } finally {
      refreshReplicasBtn && (refreshReplicasBtn.disabled = false);
    }
  }

  async function manualRefreshReplicas() {
    try {
      refreshReplicasManualBtn && (refreshReplicasManualBtn.disabled = true);
      const res = await fetch('/admin/refresh-replicas');
      const text = await res.text();
      const div = document.createElement('div');
      div.className = 'item';
      div.textContent = `🔄 ${text}`;
      healthEl.appendChild(div);
    } catch (e) {
      const div = document.createElement('div');
      div.className = 'item';
      div.textContent = 'Error refreshing replicas';
      healthEl.appendChild(div);
    } finally {
      refreshReplicasManualBtn && (refreshReplicasManualBtn.disabled = false);
    }
  }

  async function triggerElection() {
    try {
      triggerElectionBtn && (triggerElectionBtn.disabled = true);
      const res = await fetch('/admin/trigger-election');
      const text = await res.text();
      const div = document.createElement('div');
      div.className = 'item';
      div.textContent = `🗳️ ${text}`;
      healthEl.appendChild(div);
      
      // Also refresh leader info to show updated leader
      setTimeout(() => refreshLeader(), 500);
    } catch (e) {
      const div = document.createElement('div');
      div.className = 'item error';
      div.textContent = `❌ Election trigger failed: ${e.message}`;
      healthEl.appendChild(div);
    } finally {
      triggerElectionBtn && (triggerElectionBtn.disabled = false);
    }
  }

  async function setPartitionMode(enable) {
    try {
      const url = enable ? '/admin/partition/enable' : '/admin/partition/disable';
      const res = await fetch(url);
      if (!res.ok) throw new Error('Failed');
      const txt = await res.text();
      const div = document.createElement('div');
      div.className = 'item';
      div.textContent = `🧪 ${txt}`;
      healthEl.appendChild(div);
    } catch (e) {
      const div = document.createElement('div');
      div.className = 'item';
      div.textContent = 'Error toggling partition mode';
      healthEl.appendChild(div);
    }
  }

  function escapeHtml(s) {
    return s.replace(/[&<>"]?/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c] || c));
  }

  async function onSend(e) {
    e.preventDefault();
    const sender = senderSelect.value;
    const receiver = receiverSelect.value;
    const payload = document.getElementById('payload').value.trim();
    
    if (!sender || !receiver || !payload) {
      sendStatus.textContent = 'Please select sender and receiver servers';
      sendStatus.className = 'status error';
      return;
    }
    
    if (sender === receiver && receiver !== 'BROADCAST') {
      sendStatus.textContent = 'Sender and receiver cannot be the same server';
      sendStatus.className = 'status error';
      return;
    }

    sendStatus.textContent = 'Sending message between servers...';
    sendStatus.className = 'status';
    try {
      const res = await fetch('/api/messages/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sender, receiver, payload })
      });
      if (!res.ok) {
        const text = await res.text().catch(()=>'');
        throw new Error('Failed: ' + text);
      }
      const displayReceiver = receiver === 'BROADCAST' ? 'ALL SERVERS' : receiver;
      sendStatus.textContent = `Message sent from ${sender} to ${displayReceiver}`;
      sendStatus.className = 'status success';
      document.getElementById('payload').value = '';
      
      // Refresh messages immediately after sending
      refreshMessages();
    } catch (e) {
      sendStatus.textContent = 'Error sending message between servers: ' + (e && e.message ? e.message : 'Unknown error');
      sendStatus.className = 'status error';
    }
  }

  function updateCurrentServerDisplay() {
    const currentServerEl = document.getElementById('current-server');
    if (currentServerEl) {
      currentServerEl.textContent = `Current Server: ${currentServerId} (${currentServer})`;
    }
    console.log('Current server ID:', currentServerId, 'Current server:', currentServer);
  }

  refreshNodesBtn && refreshNodesBtn.addEventListener('click', refreshNodes);
  refreshMessagesBtn && refreshMessagesBtn.addEventListener('click', refreshMessages);
  refreshHeartbeatsBtn && refreshHeartbeatsBtn.addEventListener('click', () => { healthEl.innerHTML=''; refreshHeartbeats(); });
  refreshLeaderBtn && refreshLeaderBtn.addEventListener('click', () => { healthEl.innerHTML=''; refreshLeader(); });
  refreshReplicasBtn && refreshReplicasBtn.addEventListener('click', () => { healthEl.innerHTML=''; refreshReplicas(); });
  refreshReplicasManualBtn && refreshReplicasManualBtn.addEventListener('click', () => { healthEl.innerHTML=''; manualRefreshReplicas(); });
  triggerElectionBtn && triggerElectionBtn.addEventListener('click', () => { healthEl.innerHTML=''; triggerElection(); });
  enablePartitionBtn && enablePartitionBtn.addEventListener('click', () => setPartitionMode(true));
  disablePartitionBtn && disablePartitionBtn.addEventListener('click', () => setPartitionMode(false));
  sendForm && sendForm.addEventListener('submit', onSend);

  // Initialize current server display
  updateCurrentServerDisplay();

  return { refreshNodes, refreshMessages, updateCurrentServerDisplay };
})();

window.DMS = DMS;

