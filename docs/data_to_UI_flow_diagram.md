```mermaid
graph TD
%% General style for black text if not overridden
classDef default color:#000,fill:#fff,stroke:#333;

    %% Define a class for invisible spacer nodes
    classDef invisibleSpacerStyle color:transparent,fill:transparent,stroke:transparent,fontSize:20px;


    subgraph "Data Layer (Model & Persistence)"
        dl_spacer[" "]:::invisibleSpacerStyle %% Spacer node
        DM[("TaskHistory Data Model <br/>(com.homeostasis.app.data.model.TaskHistory <br/> id, taskId, userId, completedAt, points, ... )")]:::dataStyle
        DB[(Room Database <br/> app_database - task_history table)]:::dataStyle
        THDAO["TaskHistoryDao <br/> (@Dao interface) <br/> - getAllTaskHistoryFlow(): Flow&lt;List&lt;TaskHistory&gt;&gt; <br/> - insert(TaskHistory)"]:::dataStyle

        dl_spacer --> DM
        DM --> DB
        DB --> THDAO
    end

    subgraph "Repository Layer (Optional but good practice)"
        rl_spacer[" "]:::invisibleSpacerStyle %% Spacer node
        %% If THREPO were active, it would be:
        %% THREPO["TaskHistoryRepository..."]:::repoStyle
        %% rl_spacer --> THREPO
        %% THDAO --> THREPO
        %% THREPO --> VM

        %% Since Repository is empty, the spacer needs to affect something.
        %% We want it to push down the conceptual start of content in this box.
        %% The main arrow THDAO --> VM passes through here.
        %% Connecting spacer to THDAO (even if external) might influence THDAO's Y-position slightly
        %% if Mermaid's layout considers it.
        rl_spacer -.-> VM %% This is a guess: connect spacer to what would be next if Repo had content
                         %% Or, leave rl_spacer without an outgoing arrow if the subgraph is truly empty.
                         %% The subgraph itself acts as a container.
    end

    subgraph "ViewModel Layer (UI Logic)"
        vml_spacer[" "]:::invisibleSpacerStyle %% Spacer node
        VM["TaskHistoryViewModel <br/> (@HiltViewModel) <br/> - taskHistoryDao: TaskHistoryDao <br/> - _taskHistoryItems: MutableStateFlow&lt;List&lt;TaskHistory&gt;&gt; <br/> - taskHistoryItems: StateFlow&lt;List&lt;TaskHistory&gt;&gt; <br/> - loadTaskHistoryData()"]:::vmStyle

        vml_spacer --> VM
    end

    subgraph "UI Layer (Fragment & Adapter)"
        uil_spacer[" "]:::invisibleSpacerStyle %% Spacer node
        FRAG["TaskHistoryFragment <br/> (@AndroidEntryPoint) <br/> - viewModel: TaskHistoryViewModel <br/> - recyclerView: RecyclerView <br/> - taskHistoryAdapter: TaskHistoryAdapter <br/> - observeViewModel()"]:::uiStyle
        RV[RecyclerView]:::uiStyle
        ADAPTER["TaskHistoryAdapter <br/> (extends RecyclerView.Adapter or ListAdapter) <br/> - historyItems: MutableList&lt;TaskHistory&gt; (or handled by ListAdapter) <br/> - updateData() / submitList() <br/> - TaskHistoryViewHolder"]:::uiStyle
        VH["TaskHistoryViewHolder <br/> - Binds TaskHistory data to item_task_history.xml views"]:::uiStyle
        ITEM_XML[("item_task_history.xml <br/> (Layout for a single history item)")]:::uiStyle
        RV_UI{{"Displayed List in UI"}}:::uiStyle

        uil_spacer --> FRAG
        FRAG --> RV
        FRAG --> ADAPTER
        ADAPTER --> VH
        VH --> ITEM_XML
        ITEM_XML --> RV_UI
        RV -.-> RV_UI
    end

    subgraph "Dependency Injection (Hilt)"
        dil_spacer[" "]:::invisibleSpacerStyle %% Spacer node
        HILT_MOD["FirebaseModule / DatabaseModule <br/> - provideTaskHistoryDao()"]:::diStyle

        dil_spacer --> HILT_MOD
    end

    %% Connections between subgraphs (defined after subgraphs for clarity)
    THDAO --> VM
    VM --> FRAG
    HILT_MOD --> THDAO
    HILT_MOD --> VM


    %% Styles for different layers with black text
    classDef dataStyle color:#000,fill:#f9f,stroke:#333,stroke-width:2px;
    classDef repoStyle color:#000,fill:#eef,stroke:#333,stroke-width:2px;
    classDef vmStyle color:#000,fill:#ccf,stroke:#333,stroke-width:2px;
    classDef uiStyle color:#000,fill:#cfc,stroke:#333,stroke-width:2px;
    classDef diStyle color:#000,fill:#eee,stroke:#333,stroke-width:1px,stroke-dasharray: 5 5;

    %% Visual Data Flow Arrows (some of these are already covered by the main inter-subgraph connections)
    %% DM -.-> THDAO %% Already implied by DM --> DB --> THDAO
    %% DB -.-> THDAO %% Already there
    %% FRAG -.-> ADAPTER %% Already there
    %% ADAPTER -.-> VH %% Already there
    %% VH -.-> ITEM_XML %% Already there
    %% ITEM_XML -.-> RV_UI %% Already there