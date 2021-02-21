package crux.bphc.cms.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import crux.bphc.cms.R
import crux.bphc.cms.helper.CourseDataHandler
import crux.bphc.cms.helper.CourseRequestHandler
import crux.bphc.cms.interfaces.ClickListener
import crux.bphc.cms.models.forum.Discussion
import crux.bphc.cms.utils.Utils
import crux.bphc.cms.widgets.HtmlTextView
import io.realm.Realm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.*

/**
 * A simple [Fragment] subclass.
 * Use the [ForumFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ForumFragment : Fragment() {

    private var courseId = -1
    private var forumId = 1
    private var courseName: String = ""

    private lateinit var courseRequestHandler: CourseRequestHandler

    private lateinit var mAdapter: Adapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        courseId = requireArguments().getInt(COURSE_ID_KEY, -1)
        forumId = requireArguments().getInt(FORUM_ID_KEY, -1)
        courseName = requireArguments().getString(COURSE_NAME_KEY, "")

        courseRequestHandler = CourseRequestHandler()
    }

    override fun onStart() {
        requireActivity().title = courseName
        super.onStart()
    }

    override fun onCreateView(inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_forum, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emptyView = view.findViewById(R.id.tv_empty)
        swipeRefresh = view.findViewById(R.id.swipeRefreshLayout)
        swipeRefresh.setOnRefreshListener { refreshContent() }
        val recyclerView = view.findViewById<RecyclerView>(R.id.discussions)

        val mClickListener = ClickListener { `object`: Any, _: Int ->
            val discussion = `object` as Discussion
            val fragment = DiscussionFragment.newInstance(
                courseId,
                forumId,
                discussion.discussionId,
                courseName
            )
            requireActivity().supportFragmentManager.commit {
                addToBackStack(null)
                replace((requireView().parent as ViewGroup).id, fragment, "ForumDetail")
            }
            true
        }

        mAdapter = Adapter(mClickListener, ArrayList())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = mAdapter

        refreshContent()
    }

    private fun refreshContent() {
        swipeRefresh.isRefreshing = true
        CoroutineScope(Dispatchers.IO).launch {
            val realm = Realm.getDefaultInstance()
            val courseDataHandler = CourseDataHandler(realm)

            try {
                val discussions = courseRequestHandler.getForumDicussionsSync(forumId)
                for (discussion in discussions) {
                    discussion.forumId = forumId
                }

                courseDataHandler.setForumDiscussions(forumId, discussions)
                realm.close()

                CoroutineScope(Dispatchers.Main).launch {
                    mAdapter.clearDiscussions()
                    mAdapter.addDiscussions(discussions)
                    swipeRefresh.isRefreshing = false
                }
            } catch (e: IOException) {
                val realmDiscussions = courseDataHandler.getForumDiscussions(forumId)
                val discussions = realm.copyFromRealm(realmDiscussions)
                CoroutineScope(Dispatchers.Main).launch {
                    if (discussions.size == 0) {
                        emptyView.visibility = View.VISIBLE
                        Toast
                            .makeText(context, getString(R.string.no_cached_data), Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        Toast.makeText(
                            context, getString(R.string.loading_cached_data),
                            Toast.LENGTH_SHORT,
                        ).show()
                        mAdapter.clearDiscussions()
                        mAdapter.addDiscussions(discussions)
                    }
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private inner class Adapter(
        private val clickListener: ClickListener,
        val mDiscussions: MutableList<Discussion>,
    ): RecyclerView.Adapter<Adapter.ForumViewHolder>() {

        fun addDiscussions(discussions: List<Discussion>) {
            mDiscussions.addAll(discussions)
            notifyDataSetChanged()
        }

        fun clearDiscussions() {
            mDiscussions.clear()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ForumViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return ForumViewHolder(inflater.inflate(R.layout.row_forum, parent, false))
        }

        override fun onBindViewHolder(holder: ForumViewHolder, position: Int) {
            val discussion = mDiscussions[position]
            holder.setIsRecyclable(false)
            holder.bind(discussion)
        }

        override fun getItemCount(): Int {
            return mDiscussions.size
        }

        inner class ForumViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val mUserPic: ImageView
            private val mPinned: ImageView
            private val mSubject: TextView
            private val mUserName: TextView
            private val mModifiedTime: TextView
            private val mMessage: HtmlTextView

            fun bind(discussion: Discussion) {
                Glide.with(mUserPic.context).load(discussion.userPictureUrl).into(mUserPic)

                mSubject.text = discussion.subject
                mUserName.text = discussion.userFullName
                mMessage.text = discussion.message
                mModifiedTime.text = Utils.formatDate(discussion.timeModified)

                if (!discussion.isPinned) {
                    mPinned.visibility = View.GONE
                }
            }

            init {
                itemView.setOnClickListener {
                    clickListener.onClick(mDiscussions[layoutPosition], layoutPosition)
                }
                mUserPic = itemView.findViewById(R.id.user_pic)
                mSubject = itemView.findViewById(R.id.subject)
                mUserName = itemView.findViewById(R.id.user_name)
                mModifiedTime = itemView.findViewById(R.id.modified_time)
                mMessage = itemView.findViewById(R.id.message)
                mPinned = itemView.findViewById(R.id.pinned)
                itemView.findViewById<View>(R.id.click_wrapper).setOnClickListener {
                    val position = layoutPosition
                    clickListener.onClick(mDiscussions[position], position)
                }
            }
        }
    }

    companion object {
        const val COURSE_ID_KEY = "courseId"
        const val FORUM_ID_KEY = "forumId"
        const val COURSE_NAME_KEY = "courseName"

        @JvmOverloads
        fun newInstance(
            courseId: Int = -1,
            forumId: Int = 1,
            courseName: String = "Site News",
        ): ForumFragment {
            val fragment = ForumFragment()
            val args = Bundle()
            args.putInt(FORUM_ID_KEY, forumId)
            args.putInt(COURSE_ID_KEY, courseId)
            args.putString(COURSE_NAME_KEY, courseName)
            fragment.arguments = args
            return fragment
        }
    }
}