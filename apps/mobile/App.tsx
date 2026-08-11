import React, { useEffect, useState } from 'react';
import {
  StatusBar,
  StyleSheet,
  Text,
  useColorScheme,
  View,
} from 'react-native';
import { fogNode } from './src/fog/fogNode';
import type { FogNodeState } from './src/fog/fogNode';
import { BASE_URL } from './src/fog/fogNode';

function App() {
  const isDarkMode = useColorScheme() === 'dark';
  const [state, setState] = useState<FogNodeState | null>(null);

  useEffect(() => {
    let mounted = true;
    const unsubscribe = fogNode.subscribe(next => {
      if (mounted) setState(next);
    });
    void fogNode.start();
    return () => {
      mounted = false;
      unsubscribe();
      fogNode.stop();
    };
  }, []);

  const statusLabel = state?.unauthorized
    ? 'Reautenticación requerida'
    : state?.token
    ? 'Conectado'
    : 'Sin credenciales';

  return (
    <>
      <StatusBar barStyle={isDarkMode ? 'light-content' : 'dark-content'} />
      <View style={styles.screen}>
        <View style={styles.badge}>
          <Text style={styles.badgeText}>NODO FOG</Text>
        </View>
        <Text style={styles.title}>AnxietyWatch</Text>
        <Text style={styles.subtitle}>Puente reloj → backend</Text>

        <View style={styles.card}>
          <Text style={styles.cardLabel}>API</Text>
          <Text style={styles.cardValue}>{BASE_URL}</Text>
        </View>
        <View style={styles.card}>
          <Text style={styles.cardLabel}>Estado</Text>
          <Text style={styles.cardValue}>{statusLabel}</Text>
        </View>
        <View style={styles.card}>
          <Text style={styles.cardLabel}>Usuario</Text>
          <Text style={styles.cardValue}>
            {state?.identity.userId || 'Sin vincular'}
          </Text>
        </View>
        <View style={styles.card}>
          <Text style={styles.cardLabel}>Dispositivo</Text>
          <Text style={styles.cardValue}>
            {state?.identity.deviceId || 'Sin asignar'}
          </Text>
        </View>
        <View style={styles.card}>
          <Text style={styles.cardLabel}>Sobres pendientes del reloj</Text>
          <Text style={styles.cardValue}>{state?.pending ?? '…'}</Text>
        </View>
        <View style={styles.card}>
          <Text style={styles.cardLabel}>Última entrega</Text>
          <Text style={styles.cardValue}>
            {state?.lastDelivery
              ? `${state.lastDelivery.kind}: ${state.lastDelivery.status}`
              : 'Sin entregas'}
          </Text>
        </View>

        <Text style={styles.notice}>
          El reloj entrega telemetría y eventos SOS por Wear Data Layer; este
          nodo los enriquece y envía al API de producción.
        </Text>
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#0D1715',
    paddingHorizontal: 24,
    paddingVertical: 32,
  },
  badge: {
    alignSelf: 'flex-start',
    backgroundColor: '#17362E',
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  badgeText: {
    color: '#9FE0C8',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1.2,
  },
  title: {
    color: '#F2F7F5',
    fontSize: 36,
    fontWeight: '700',
    marginTop: 28,
  },
  subtitle: {
    color: '#AEBDB8',
    fontSize: 17,
    marginBottom: 32,
    marginTop: 8,
  },
  card: {
    backgroundColor: '#14221F',
    borderColor: '#24423A',
    borderRadius: 18,
    borderWidth: 1,
    marginBottom: 12,
    padding: 20,
  },
  cardLabel: {
    color: '#89A39B',
    fontSize: 13,
    textTransform: 'uppercase',
  },
  cardValue: {
    color: '#E6F0EC',
    fontSize: 16,
    fontWeight: '600',
    marginTop: 6,
  },
  notice: {
    color: '#93A29D',
    fontSize: 13,
    lineHeight: 19,
    marginTop: 'auto',
  },
});

export default App;
