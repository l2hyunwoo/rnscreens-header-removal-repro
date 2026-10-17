import React, {createContext, useContext, useEffect, useState} from 'react';
import { NavigationContainer } from '@react-navigation/native';
import {
  createNativeStackNavigator,
  NativeStackScreenProps,
} from '@react-navigation/native-stack';
import { BackHandler, Button, Image, StyleSheet, Text, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

type OuterRoutes = { Home: undefined; Workspace: undefined };
type MiddleRoutes = { Landing: undefined; InnerFlow: undefined };
type InnerRoutes = { Menu: undefined; Form: undefined };

const Outer = createNativeStackNavigator<OuterRoutes>();
const Middle = createNativeStackNavigator<MiddleRoutes>();
const Inner = createNativeStackNavigator<InnerRoutes>();
const HeaderRevision = createContext({revision: 0, update: () => {}});

function Logo() {
  return (
    <Image
      source={require('./assets/backButton.png')}
      style={styles.logo}
    />
  );
}

function CustomHeader() {
  return (
    <View>
      <Text style={styles.header}>Nested header: LEFT + CENTER</Text>
    </View>
  );
}

function Home({ navigation }: NativeStackScreenProps<OuterRoutes, 'Home'>) {
  return (
    <View style={styles.screen}>
      <Text style={styles.title}>PR #3777</Text>
      <Text>Three nested native stacks with custom header subviews.</Text>
      <Button
        title="Open middle stack"
        testID="3777-open-middle"
        onPress={() => navigation.navigate('Workspace')}
      />
    </View>
  );
}

function Landing({
  navigation,
}: NativeStackScreenProps<MiddleRoutes, 'Landing'>) {
  return (
    <View style={styles.screen}>
      <Text style={styles.title}>Middle stack</Text>
      <Text>After two Back presses, this screen should remain usable.</Text>
      <Button
        title="Open inner stack"
        testID="3777-open-inner"
        onPress={() => navigation.navigate('InnerFlow')}
      />
    </View>
  );
}

function Menu({ navigation }: NativeStackScreenProps<InnerRoutes, 'Menu'>) {
  return (
    <View style={styles.screen}>
      <Text style={styles.title}>Inner menu</Text>
      <Text>Wait for this transition to finish, then open the form.</Text>
      <Button
        title="Open form"
        testID="3777-open-form"
        onPress={() => navigation.navigate('Form')}
      />
    </View>
  );
}

function Form() {
  const {revision, update} = useContext(HeaderRevision);
  return (
    <View style={styles.screen}>
      <Text style={styles.title}>Inner form</Text>
      <Text>Wait until the screen settles.</Text>
      <Text>Press system Back twice, about 450 ms apart.</Text>
      <Text>First: Form → Menu. Second: Inner stack → Middle stack.</Text>
      <Text>Keep Android animation scales enabled.</Text>
      <Text>Header revision: {revision}</Text>
      <Button title="Refresh header after BEGIN" testID="3777-refresh-header" onPress={update} />
    </View>
  );
}

function InnerNavigator() {
  const {revision} = useContext(HeaderRevision);
  return (
    <Inner.Navigator
      screenOptions={{
        animation: 'default',
        headerLeft: Logo,
        headerTitle: CustomHeader,
        headerTitleAlign: 'center',
        headerTintColor: revision % 2 ? '#f00' : '#00f',
      }}>
      <Inner.Screen name="Menu" component={Menu} />
      <Inner.Screen name="Form" component={Form} />
    </Inner.Navigator>
  );
}

function MiddleNavigator() {
  return (
    <Middle.Navigator screenOptions={{ animation: 'default' }}>
      <Middle.Screen name="Landing" component={Landing} />
      <Middle.Screen
        name="InnerFlow"
        component={InnerNavigator}
        options={{ headerShown: false }}
      />
    </Middle.Navigator>
  );
}

declare const globalThis: {performance: {now(): number}};
let backSeq = 0;

// Registered after NavigationContainer, so BackHandler calls it before React Navigation.
function BackProbe() {
  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      backSeq += 1;
      console.info(`ReproBack js seq=${backSeq} perfMs=${globalThis.performance.now().toFixed(1)} wallMs=${Date.now()}`);
      return false;
    });
    return () => sub.remove();
  }, []);
  return null;
}

export default function App() {
  const [revision, setRevision] = useState(0);
  return (
    <HeaderRevision.Provider value={{revision, update: () => setRevision(value => value + 1)}}>
    <SafeAreaProvider>
      <NavigationContainer
        onStateChange={state =>
          console.info(`ReproNav perfMs=${globalThis.performance.now().toFixed(1)} routes=${JSON.stringify(routeNames(state))}`)
        }>
        <Outer.Navigator screenOptions={{ animation: 'default' }}>
          <Outer.Screen name="Home" component={Home} />
          <Outer.Screen
            name="Workspace"
            component={MiddleNavigator}
            options={{ headerShown: false }}
          />
        </Outer.Navigator>
      </NavigationContainer>
      <BackProbe />
    </SafeAreaProvider>
    </HeaderRevision.Provider>
  );
}

function routeNames(state: any): unknown {
  return state?.routes?.map((r: any) => (r.state ? [r.name, routeNames(r.state)] : r.name));
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    justifyContent: 'center',
    padding: 24,
    gap: 20,
    backgroundColor: '#eef4ff',
  },
  title: { fontSize: 28, fontWeight: '600', color: '#14213d' },
  header: { fontSize: 16, color: '#14213d' },
  logo: { width: 24, height: 24 },
});
