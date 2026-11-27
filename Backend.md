### AiChat
  this app allows the user to talk to any llm, and run in an agent loop
* the user can send the file  and messaged etc and we might have to feed it to the agent
* we will maintain the state b/w backend and client, the client will send the chatId:String|null null means the new chat and string means perv one; after we done with a successful response we'll store the new message + response in the db and also stream it 


### Code-style:
* hey the repo follow the clean code(this is general good practice and not dogma), see further rules to see what I mean by that
* for eg if you are working on a handler fun that does something the types should be in their place and the business logic in it's