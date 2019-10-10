declare namespace RNDingTalkShareModule {
  function isSupported(): Promise<boolean>;
  function isInstalled(): Promise<boolean>;
  function shareWebPage(url: string, thumb: string, title: string, content: string): Promise<string>;
  function shareImage(image: string): Promise<string>;
  function getAuthCode(): Promise<{ code: string }>;
}

export default RNDingTalkShareModule;
