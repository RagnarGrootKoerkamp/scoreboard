package me.hex539.app.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import me.hex539.app.intent.IntentUtils;
import me.hex539.app.R;
import me.hex539.app.view.ScoreboardRowView;
import org.domjudge.api.DomjudgeRest;
import org.domjudge.proto.DomjudgeProto;

public class LiveScoreboardActivity extends Activity {
  private static final String TAG = LiveScoreboardActivity.class.getSimpleName();

  public static class Extras {
    private Extras() {}

    public static String URI = "uri";
  }

  private static class ContestHandler extends Handler {
    public static final int MSG_CREATE = 1;

    @Override
    public void handleMessage(Message message) {
      switch (message.what) {
        case MSG_CREATE: {

          break;
        }
        default:
          throw new IllegalArgumentException("Bad message ID: " + message.what);
      }
    }
  }
  private Handler mApiHandler;
  private HandlerThread mApiHandlerThread;

  private DomjudgeRest mApi;
  private DomjudgeProto.Contest mContest;

  @Override
  public void onCreate(Bundle savedState) {
    super.onCreate(savedState);
    if (!IntentUtils.validateIntent(getIntent(), Extras.class, TAG)) {
      finish();
      return;
    }

    setContentView(R.layout.scoreboard);

    mApiHandlerThread = new HandlerThread("api");
    mApiHandlerThread.start();
    mApiHandler = new Handler(mApiHandlerThread.getLooper());

    final String uri = getIntent().getStringExtra(Extras.URI);
    mApiHandler.post(() -> {
      try {
        mApi = new DomjudgeRest(uri);
        mContest = mApi.getContest();
      } catch (Exception e) {
        Log.e(TAG, "Failed to fetch active contest", e);
        finish();
        return;
      }
      runOnUiThread(() -> {
        ((TextView) findViewById(R.id.contest_name)).setText(mContest.getName());
      });
      mApiHandler.post(() -> {
        final DomjudgeProto.Team[] teams;
        try{
          teams = mApi.getTeams();
        } catch (Exception e) {
          Log.e(TAG, "Failed to fetch teams list", e);
          finish();
          return;
        }
        runOnUiThread(() -> {
          final RecyclerView scoreboardRows = (RecyclerView) findViewById(R.id.scoreboard_rows);
          scoreboardRows.setAdapter(new Adapter(teams));
          /*
          for (DomjudgeProto.Team team : teams) {
            final ScoreboardRowView row = new ScoreboardRowView(LiveScoreboardActivity.this);
            row.setTeam(team);
            scoreboardRows.addView(row);
          }
          */
        });
      });
    });

//    DomjudgeRest api = new DomjudgeRest(uri);
//    DomjudgeProto.Contest contest = api.getContest();
//    DomjudgeProto.ScoreboardRow[] scoreboard = api.getScoreboard(contest);

  }

  private static class ViewHolder extends RecyclerView.ViewHolder {
    final ScoreboardRowView view;

    public ViewHolder(View v) {
      super(v);
      view = (ScoreboardRowView) v;
    }
  }

  private static class Adapter extends RecyclerView.Adapter<ViewHolder> {
    private final DomjudgeProto.Team[] mTeams;

    public Adapter(DomjudgeProto.Team[] teams) {
      mTeams = teams;
    }

    @Override
    public int getItemCount() {
      return mTeams.length;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
      return new ViewHolder(new ScoreboardRowView(viewGroup.getContext()));
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int position) {
      viewHolder.view.setTeam(mTeams[position]);
    }
  }

  @Override
  public void onDestroy() {
    if (mApiHandlerThread != null) {
      mApiHandlerThread.interrupt();
    }
    super.onDestroy();
  }
}
