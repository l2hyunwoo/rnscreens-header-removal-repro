import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Button,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {
  Screen,
  ScreenStack,
  ScreenStackHeaderCenterView,
  ScreenStackHeaderConfig,
  ScreenStackHeaderLeftView,
  ScreenStackHeaderRightView,
  Tabs,
} from 'react-native-screens';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';

type TabKey = 'TabA' | 'TabB' | 'Stable';
const DELAYS = [0, 100, 450, 1000, 3000];

export default function Test3777Tabs() {
  const [tab, setTab] = useState<'TabA' | 'TabB'>('TabA');
  const [revision, setRevision] = useState(0);
  const [presses, setPresses] = useState(0);
  const [loading, setLoading] = useState(false);
  const [delay, setDelay] = useState(450);
  const [operation, setOperation] = useState('Initial mount');

  useEffect(() => {
    if (!loading) {
      return;
    }
    const timer = setTimeout(() => setLoading(false), delay);
    return () => clearTimeout(timer);
  }, [loading, delay]);

  function swap() {
    setTab(current => (current === 'TabA' ? 'TabB' : 'TabA'));
    setRevision(current => current + 1);
  }

  const controls = (
    <>
      <Text style={styles.title}>Tabs + legacy header removal</Text>
      <Text testID="3777-tabs-status">
        Route set: {tab} + Stable | revision {revision} | presses {presses}
      </Text>
      <Text>Last action: {operation}</Text>
      <Text>Loader delay: {delay} ms</Text>
      <View style={styles.delays}>
        {DELAYS.map(value => (
          <Button
            key={value}
            title={`${value} ms`}
            disabled={delay === value}
            testID={`3777-tabs-delay-${value}`}
            onPress={() => setDelay(value)}
          />
        ))}
      </View>
      <Button
        title="Swap selected tab A ↔ B"
        testID="3777-tabs-swap"
        onPress={() => {
          setOperation('Swap only');
          swap();
        }}
      />
      <Button
        title="Toggle root through loader"
        testID="3777-tabs-root"
        onPress={() => {
          setOperation('Root loader only');
          setRevision(current => current + 1);
          setLoading(true);
        }}
      />
      <Button
        title="Swap tab + toggle root"
        testID="3777-tabs-combined"
        onPress={() => {
          setOperation('Swap + root loader');
          swap();
          setLoading(true);
        }}
      />
      <Button
        title={`Verify content press (${presses})`}
        testID="3777-tabs-content-press"
        onPress={() => setPresses(current => current + 1)}
      />
      <Text>
        Select the changing tab before a swap. Check all three header slots
        after each action.
      </Text>
    </>
  );

  return (
    <SafeAreaProvider>
      {loading ? (
        <SafeAreaView style={styles.loader} testID="3777-tabs-loader">
          <ActivityIndicator size="large" />
          <Text style={styles.title}>Full-screen loader</Text>
          <Text>
            {operation}: remount {tab}, revision {revision}
          </Text>
          <Text>Delay: {delay} ms</Text>
        </SafeAreaView>
      ) : (
        <TabRoot
          tab={tab}
          revision={revision}
          presses={presses}
          onHeaderPress={() => setPresses(current => current + 1)}>
          {controls}
        </TabRoot>
      )}
    </SafeAreaProvider>
  );
}

function TabRoot({
  tab,
  revision,
  presses,
  onHeaderPress,
  children,
}: {
  tab: 'TabA' | 'TabB';
  revision: number;
  presses: number;
  onHeaderPress: () => void;
  children: React.ReactNode;
}) {
  // This state belongs to the native host lifetime and resets on root remount.
  const [selection, setSelection] = useState({
    selectedScreenKey: tab as TabKey,
    baseProvenance: 0,
  });
  // A removed selected key must never be sent to the native host.
  const selectedScreenKey =
    selection.selectedScreenKey === 'Stable' ? 'Stable' : tab;

  return (
    <Tabs.Host
      navStateRequest={{ ...selection, selectedScreenKey }}
      onTabSelected={({ nativeEvent }) => {
        if (
          nativeEvent.selectedScreenKey === tab ||
          nativeEvent.selectedScreenKey === 'Stable'
        ) {
          setSelection({
            selectedScreenKey: nativeEvent.selectedScreenKey,
            baseProvenance: nativeEvent.provenance,
          });
        }
      }}>
      {([tab, 'Stable'] as const).map(route => (
        <Tabs.Screen key={route} screenKey={route} title={route}>
          <ScreenStack style={styles.fill}>
            <Screen activityState={2} isNativeStack style={styles.fill}>
              <ScreenStackHeaderConfig title={`${route} r${revision}`}>
                <ScreenStackHeaderLeftView>
                  <Text style={styles.header}>L {revision}</Text>
                </ScreenStackHeaderLeftView>
                <ScreenStackHeaderCenterView>
                  <Text style={styles.header}>
                    {route} r{revision}
                  </Text>
                </ScreenStackHeaderCenterView>
                <ScreenStackHeaderRightView>
                  <Button
                    title={`R ${revision} (${presses})`}
                    testID={`3777-tabs-header-${route}`}
                    onPress={onHeaderPress}
                  />
                </ScreenStackHeaderRightView>
              </ScreenStackHeaderConfig>
              <SafeAreaView style={styles.fill} edges={['bottom']}>
                <ScrollView contentContainerStyle={styles.content}>
                  <Text testID={`3777-tabs-route-${route}`}>
                    Visible route: {route} | header revision {revision}
                  </Text>
                  {children}
                </ScrollView>
              </SafeAreaView>
            </Screen>
          </ScreenStack>
        </Tabs.Screen>
      ))}
    </Tabs.Host>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  content: { padding: 20, gap: 12, backgroundColor: '#eef4ff' },
  title: { fontSize: 22, fontWeight: '600', color: '#14213d' },
  header: { fontSize: 16, color: '#14213d' },
  delays: { flexDirection: 'row', flexWrap: 'wrap' },
  loader: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 20,
    backgroundColor: '#fff2d5',
  },
});
