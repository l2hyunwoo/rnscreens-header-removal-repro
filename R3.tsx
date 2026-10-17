import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import {
  createNativeStackNavigator,
  NativeStackScreenProps,
} from '@react-navigation/native-stack';
import { Button, Image, StyleSheet, Text, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

// `getId` makes `navigate` move an existing route to the top of the stack.
// Fabric mounts that reorder as Remove + Insert of the same native screen.
type Params = { id: string; visit: number };
type OuterRoutes = { Item: Params; Flow: Params };
type InnerRoutes = { Page: undefined };

const Outer = createNativeStackNavigator<OuterRoutes>();
const Inner = createNativeStackNavigator<InnerRoutes>();

function Logo() {
  return <Image source={require('./assets/backButton.png')} style={styles.logo} />;
}

function Title({ text }: { text: string }) {
  return (
    <View>
      <Text style={styles.header}>{text}</Text>
    </View>
  );
}

function Controls({
  name,
  params,
  navigate,
}: {
  name: keyof OuterRoutes;
  params: Params;
  navigate: (name: keyof OuterRoutes, params: Params) => void;
}) {
  return (
    <View style={styles.screen}>
      <Text style={styles.title}>
        {name} {params.id}
      </Text>
      <Text testID="3777-move-status">
        Visible: {name} {params.id} visit {params.visit}
      </Text>
      {(['A', 'B'] as const).map(id => (
        <Button
          key={id}
          title={`Open ${name} ${id}`}
          onPress={() => navigate(name, { id, visit: params.visit + 1 })}
        />
      ))}
    </View>
  );
}

function Item({ navigation, route }: NativeStackScreenProps<OuterRoutes, 'Item'>) {
  React.useLayoutEffect(() => {
    navigation.setOptions({
      headerTitle: () => <Title text={`Item ${route.params.id} visit ${route.params.visit}`} />,
    });
  }, [navigation, route.params]);
  return <Controls name="Item" params={route.params} navigate={navigation.navigate} />;
}

function Flow({ navigation, route }: NativeStackScreenProps<OuterRoutes, 'Flow'>) {
  const { params } = route;
  return (
    <Inner.Navigator
      screenOptions={{
        headerLeft: Logo,
        headerTitle: () => <Title text={`Flow ${params.id} visit ${params.visit}`} />,
        headerTitleAlign: 'center',
      }}>
      <Inner.Screen name="Page">
        {() => <Controls name="Flow" params={params} navigate={navigation.navigate} />}
      </Inner.Screen>
    </Inner.Navigator>
  );
}

export default function MovedScreens({ nested }: { nested: boolean }) {
  return (
    <SafeAreaProvider>
      <NavigationContainer>
        <Outer.Navigator
          initialRouteName={nested ? 'Flow' : 'Item'}
          screenOptions={{ animation: 'default', headerLeft: Logo, headerTitleAlign: 'center' }}>
          <Outer.Screen
            name="Item"
            component={Item}
            getId={({ params }) => params.id}
            initialParams={{ id: 'A', visit: 0 }}
          />
          <Outer.Screen
            name="Flow"
            component={Flow}
            getId={({ params }) => params.id}
            initialParams={{ id: 'A', visit: 0 }}
            options={{ headerShown: false }}
          />
        </Outer.Navigator>
      </NavigationContainer>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, justifyContent: 'center', padding: 24, gap: 20, backgroundColor: '#eef4ff' },
  title: { fontSize: 28, fontWeight: '600', color: '#14213d' },
  header: { fontSize: 16, color: '#14213d' },
  logo: { width: 24, height: 24 },
});
