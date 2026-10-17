import React, {useState} from 'react';
import {Button, SafeAreaView, Text, View} from 'react-native';
import NestedStacks from './R1';
import NativeTabs from './R2';

export default function App() {
  const [scenario, setScenario] = useState<'menu' | 'stacks' | 'tabs'>('menu');
  if (scenario === 'stacks') {
    return <NestedStacks />;
  }
  if (scenario === 'tabs') {
    return <NativeTabs />;
  }
  return (
    <SafeAreaView style={{flex: 1, justifyContent: 'center', padding: 24}}>
      <Text>Header removal comparison</Text>
      <View style={{height: 20}} />
      <Button title="Nested stack and two Back events" onPress={() => setScenario('stacks')} />
      <Button title="Native tab removal and remount" onPress={() => setScenario('tabs')} />
    </SafeAreaView>
  );
}
