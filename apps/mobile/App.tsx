import React from 'react';
import {
  StatusBar,
  StyleSheet,
  Text,
  useColorScheme,
  View,
} from 'react-native';

function App() {
  const isDarkMode = useColorScheme() === 'dark';

  return (
    <>
      <StatusBar barStyle={isDarkMode ? 'light-content' : 'dark-content'} />
      <View style={styles.screen}>
        <View style={styles.badge}>
          <Text style={styles.badgeText}>BASE LOCAL</Text>
        </View>
        <Text style={styles.title}>AnxietyWatch</Text>
        <Text style={styles.subtitle}>Coordinador móvil preparado</Text>

        <View style={styles.card}>
          <Text style={styles.cardLabel}>Reloj</Text>
          <Text style={styles.cardValue}>Pendiente de vinculación</Text>
        </View>
        <View style={styles.card}>
          <Text style={styles.cardLabel}>Sincronización</Text>
          <Text style={styles.cardValue}>Modo offline disponible</Text>
        </View>

        <Text style={styles.notice}>
          Herramienta de bienestar y apoyo. Esta información no representa un
          diagnóstico médico.
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
    fontSize: 18,
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
