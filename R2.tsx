import React, { useEffect, useRef, useState } from 'react';
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
// Negative: root removal precedes swap. Positive: root removal follows swap.
const ROOT_OFFSETS = [-450, -100, -16, 0, 16, 100, 450];
// How the loader takes over the navigator's place:
// unmount: the navigator subtree is deleted and a new one is mounted afterwards.
// hide: the navigator stays mounted under display: none.
// wrap: the navigator stays mounted; its wrapper gains opacity, so Fabric
//   un-flattens the wrapper and moves the same native views into a new parent.
const ROOT_MODES = ['unmount', 'hide', 'wrap'] as const;
type RootMode = (typeof ROOT_MODES)[number];

export default function Test3777Tabs() {
  const [tab, setTab] = useState<'TabA' | 'TabB'>('TabA');
  const [revision, setRevision] = useState(0);
  const [presses, setPresses] = useState(0);
  const [loading, setLoading] = useState(false);
  const [delay, setDelay] = useState(450);
  const [operation, setOperation] = useState('Initial mount');
  const [rootOffset, setRootOffset] = useState(0);
  const [rootMode, setRootMode] = useState<RootMode>('unmount');
  const sequence = useRef(0);
  const pendingTimers = useRef<ReturnType<typeof setTimeout>[]>([]);

  function trace(event: string) {
    console.info(`ReproTabs id=${sequence.current} event=${event} jsWallMs=${Date.now()}`);
  }

  useEffect(() => () => pendingTimers.current.forEach(clearTimeout), []);
  useEffect(() => {
    trace(loading ? 'loader-committed' : 'tabs-committed');
  }, [loading, tab, revision]);

  useEffect(() => {
    if (!loading) {
      return;
    }
    const timer = setTimeout(() => {
      trace('root-mount-request');
      setLoading(false);
    }, delay);
    return () => clearTimeout(timer);
  }, [loading, delay]);

  function swap() {
    trace('tab-swap-request');
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
        title={`Root offset: ${rootOffset} ms`}
        testID="3777-tabs-root-offset"
        onPress={() => setRootOffset(current => ROOT_OFFSETS[(ROOT_OFFSETS.indexOf(current) + 1) % ROOT_OFFSETS.length])}
      />
      <Button
        title={`Root mode: ${rootMode}`}
        testID="3777-tabs-root-mode"
        onPress={() => setRootMode(current => ROOT_MODES[(ROOT_MODES.indexOf(current) + 1) % ROOT_MODES.length])}
      />
      <Button
        title="Swap selected tab A ↔ B"
        testID="3777-tabs-swap"
        onPress={() => {
          sequence.current += 1;
          setOperation('Swap only');
          swap();
        }}
      />
      <Button
        title="Toggle root through loader"
        testID="3777-tabs-root"
        onPress={() => {
          sequence.current += 1;
          trace('root-unmount-request');
          setOperation('Root loader only');
          setRevision(current => current + 1);
          setLoading(true);
        }}
      />
      <Button
        title="Swap tab + toggle root"
        testID="3777-tabs-combined"
        onPress={() => {
          sequence.current += 1;
          setOperation(`Swap + root loader (offset ${rootOffset} ms)`);
          const unmount = () => {
            trace('root-unmount-request');
            setLoading(true);
          };
          if (rootOffset === 0) {
            swap();
            unmount();
          } else {
            (rootOffset < 0 ? unmount : swap)();
            pendingTimers.current.push(setTimeout(rootOffset < 0 ? swap : unmount, Math.abs(rootOffset)));
          }
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

  const loader = (
    <SafeAreaView
      style={[styles.loader, rootMode !== 'unmount' && StyleSheet.absoluteFill]}
      testID="3777-tabs-loader">
      <ActivityIndicator size="large" />
      <Text style={styles.title}>Full-screen loader</Text>
      <Text>
        {operation}: remount {tab}, revision {revision}
      </Text>
      <Text>Delay: {delay} ms</Text>
    </SafeAreaView>
  );
  const tabRoot = (
    <TabRoot
      tab={tab}
      revision={revision}
      presses={presses}
      onHeaderPress={() => setPresses(current => current + 1)}>
      {controls}
    </TabRoot>
  );

  if (rootMode !== 'unmount') {
    return (
      <SafeAreaProvider>
        <View
          style={[
            styles.fill,
            loading && (rootMode === 'hide' ? styles.hidden : styles.wrapped),
          ]}>
          {tabRoot}
        </View>
        {loading && loader}
      </SafeAreaProvider>
    );
  }

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
  hidden: { display: 'none' },
  wrapped: { opacity: 0.99 },
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
